v0.4.57 queue diagnostics

Debug queue timing semantics:
- queuedAtMs = local LLM request entered the FIFO.
- startedAtMs = dequeue/execution-grant timestamp for local LLM requests (historical field name retained for compatibility).
- finishedAtMs = execution completion/failure timestamp.
- queue wait duration = dequeue - enqueue, or now - enqueue while waiting.
- processing duration = finish - dequeue, or now - dequeue while running.
- Parent/orchestration processing is displayed separately and is not an LLM execution slot.
