package com.sktpj.npcbrain;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Worker-thread Binder client for crash-contained CLEF native execution. */
final class ClefGpuProcessClient {
    static final String DESCRIPTOR = "com.sktpj.npcbrain.ClefGpuService";
    static final int TRANSACTION_GET_PID = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_EVALUATE_GPU = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_EVALUATE_CPU = IBinder.FIRST_CALL_TRANSACTION + 2;

    private static final long BIND_TIMEOUT_MS = 5_000L;
    private static final long EVALUATION_TIMEOUT_MS = 120_000L;
    private static final Object LOCK = new Object();

    private static final ExecutorService IPC_EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "npcbrain-clef-native-ipc");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static IBinder binder;
    private static ServiceConnection connection;
    private static int servicePid = -1;

    private ClefGpuProcessClient() {
    }

    static double[] evaluateGpu(
            Context context,
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) throws Exception {
        Context appContext = checkedContext(context);
        ClefGpuHealthStore health = new ClefGpuHealthStore(appContext);
        if (health.isQuarantined()) {
            throw new IllegalStateException("CLEF GPU is quarantined for this build");
        }

        int pid = -1;
        try {
            IBinder remote = ensureBound(appContext);
            pid = queryServicePid(remote);
            rememberServicePid(pid);
            return transactWithTimeout(
                    remote,
                    TRANSACTION_EVALUATE_GPU,
                    modelPath,
                    state,
                    fieldIds,
                    instructions,
                    optionIds,
                    optionDescriptions,
                    optionCounts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            quarantineAndReset(appContext, health, pid);
            throw failure;
        }
    }

    static double[] evaluateCpu(
            Context context,
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) throws Exception {
        Context appContext = checkedContext(context);
        int pid = -1;
        try {
            IBinder remote = ensureBound(appContext);
            pid = queryServicePid(remote);
            rememberServicePid(pid);
            return transactWithTimeout(
                    remote,
                    TRANSACTION_EVALUATE_CPU,
                    modelPath,
                    state,
                    fieldIds,
                    instructions,
                    optionIds,
                    optionDescriptions,
                    optionCounts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            killServiceProcess(pid);
            resetConnection(appContext);
            throw failure;
        }
    }

    private static Context checkedContext(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("CLEF native IPC cannot block the main thread");
        }
        return context.getApplicationContext();
    }

    private static double[] transactWithTimeout(
            IBinder remote,
            int transaction,
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) throws Exception {
        Future<double[]> future = IPC_EXECUTOR.submit(() -> transactEvaluate(
                remote,
                transaction,
                modelPath,
                state,
                fieldIds,
                instructions,
                optionIds,
                optionDescriptions,
                optionCounts));
        try {
            double[] scores = future.get(EVALUATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (scores == null) throw new IllegalStateException("CLEF native worker returned no scores");
            return scores;
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new IllegalStateException("CLEF native worker timed out", timeout);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException("CLEF native worker failed", cause);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            throw interrupted;
        }
    }

    private static IBinder ensureBound(Context appContext) throws Exception {
        synchronized (LOCK) {
            if (binder != null && binder.isBinderAlive()) return binder;
        }

        CountDownLatch connected = new CountDownLatch(1);
        ServiceConnection newConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (LOCK) {
                    binder = service;
                }
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                synchronized (LOCK) {
                    binder = null;
                    servicePid = -1;
                }
            }

            @Override
            public void onBindingDied(ComponentName name) {
                synchronized (LOCK) {
                    binder = null;
                    servicePid = -1;
                }
                connected.countDown();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                connected.countDown();
            }
        };

        synchronized (LOCK) {
            connection = newConnection;
        }
        boolean requested = appContext.bindService(
                new Intent(appContext, ClefGpuService.class),
                newConnection,
                Context.BIND_AUTO_CREATE);
        if (!requested || !connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            resetConnection(appContext);
            throw new IllegalStateException("CLEF native service bind failed");
        }

        synchronized (LOCK) {
            if (binder == null || !binder.isBinderAlive()) {
                resetConnection(appContext);
                throw new IllegalStateException("CLEF native service is unavailable");
            }
            return binder;
        }
    }

    private static int queryServicePid(IBinder remote) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!remote.transact(TRANSACTION_GET_PID, data, reply, 0)) {
                throw new RemoteException("CLEF native PID transact failed");
            }
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static double[] transactEvaluate(
            IBinder remote,
            int transaction,
            String modelPath,
            String state,
            String[] fieldIds,
            String[] instructions,
            String[] optionIds,
            String[] optionDescriptions,
            int[] optionCounts
    ) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(modelPath);
            data.writeString(state);
            data.writeStringArray(fieldIds);
            data.writeStringArray(instructions);
            data.writeStringArray(optionIds);
            data.writeStringArray(optionDescriptions);
            data.writeIntArray(optionCounts);
            if (!remote.transact(transaction, data, reply, 0)) {
                throw new RemoteException("CLEF native evaluation transact failed");
            }
            reply.readException();
            return reply.createDoubleArray();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void rememberServicePid(int pid) {
        synchronized (LOCK) {
            servicePid = pid;
        }
    }

    private static void quarantineAndReset(
            Context appContext,
            ClefGpuHealthStore health,
            int pid
    ) {
        health.quarantine();
        killServiceProcess(pid);
        resetConnection(appContext);
    }

    private static void killServiceProcess(int pid) {
        int mainPid = Process.myPid();
        int candidate = pid;
        synchronized (LOCK) {
            if (candidate <= 0) candidate = servicePid;
        }
        if (candidate > 0 && candidate != mainPid) {
            int servicePid = candidate;
            Process.killProcess(servicePid);
        }
    }

    private static void resetConnection(Context appContext) {
        ServiceConnection current;
        synchronized (LOCK) {
            current = connection;
            connection = null;
            binder = null;
            servicePid = -1;
        }
        if (current != null) {
            try {
                appContext.unbindService(current);
            } catch (Exception ignored) {
            }
        }
    }
}
