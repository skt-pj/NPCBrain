package com.sktpj.npcbrain;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

/** Dedicated process boundary for all CLEF native execution. */
public final class ClefGpuService extends Service {
    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(ClefGpuProcessClient.DESCRIPTOR);
                return true;
            }

            data.enforceInterface(ClefGpuProcessClient.DESCRIPTOR);
            if (code == ClefGpuProcessClient.TRANSACTION_GET_PID) {
                reply.writeNoException();
                reply.writeInt(Process.myPid());
                return true;
            }
            if (code != ClefGpuProcessClient.TRANSACTION_EVALUATE_GPU
                    && code != ClefGpuProcessClient.TRANSACTION_EVALUATE_CPU) {
                return super.onTransact(code, data, reply, flags);
            }

            String modelPath = data.readString();
            String state = data.readString();
            String[] fieldIds = data.createStringArray();
            String[] instructions = data.createStringArray();
            String[] optionIds = data.createStringArray();
            String[] optionDescriptions = data.createStringArray();
            int[] optionCounts = data.createIntArray();

            try {
                double[] scores = code == ClefGpuProcessClient.TRANSACTION_EVALUATE_GPU
                        ? ClefNativeBridge.evaluateGpuOnly(
                                modelPath,
                                state,
                                fieldIds,
                                instructions,
                                optionIds,
                                optionDescriptions,
                                optionCounts)
                        : ClefNativeBridge.evaluateCpuOnly(
                                modelPath,
                                state,
                                fieldIds,
                                instructions,
                                optionIds,
                                optionDescriptions,
                                optionCounts);
                reply.writeNoException();
                reply.writeDoubleArray(scores);
            } catch (Throwable error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = error.getClass().getSimpleName();
                }
                reply.writeException(new IllegalStateException(message));
            }
            return true;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
