from dataclasses import dataclass


@dataclass(frozen=True)
class LocalSearchFilter:
    region: str = ""
    province: str = ""
    district: str = ""
    management: tuple[str, ...] = ()
    level: tuple[str, ...] = ()
