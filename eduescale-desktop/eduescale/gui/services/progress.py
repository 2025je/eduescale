from dataclasses import dataclass


@dataclass(frozen=True)
class ProgressMetrics:
    received: int
    total: int
    unique: int
    pages: int
    failed: int = 0
    resumed: int = 0
    retried: int = 0
