import ast
from pathlib import Path

SOURCE_ROOT = Path(__file__).parents[1] / "eduescale"
INNER_LAYERS = ("domain", "application")
FORBIDDEN_IMPORTS = {
    "PySide6",
    "requests",
    "scrapy",
    "sqlalchemy",
}


def test_inner_layers_do_not_depend_on_outer_layers_or_frameworks():
    violations = []
    for layer in INNER_LAYERS:
        for path in (SOURCE_ROOT / layer).rglob("*.py"):
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    modules = [alias.name for alias in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module is not None:
                    modules = [node.module]
                else:
                    continue
                for module in modules:
                    if (
                        module.startswith(("eduescale.gui", "eduescale.infrastructure"))
                        or module.split(".", maxsplit=1)[0] in FORBIDDEN_IMPORTS
                    ):
                        violations.append(f"{path.relative_to(SOURCE_ROOT)}: {module}")

    assert not violations, "\n".join(violations)
