## Workflow

Two-step process — plan and implement are always separate PRs:

1. **Plan:** `/plan-create <what to build>` → writes plan file, opens PR for review
2. **Implement:** `/plan-execute plans/<file>.md` → implements approved plan, opens separate PR

Never implement in the same session as planning.
When implementing, follow the plan exactly. If reality diverges — stop and ask first.

## Testing
- Always structure tests using AAA (Arrange, Act, Assert): group setup under `// Arrange`, the single action under `// Act`, and assertions under `// Assert`. When Act and Assert are inseparable (e.g. a loop that both acts and asserts each iteration), note this with `// Act & Assert`.

## Git
- Never add a "Co-Authored-By" trailer or "Generated with Claude Code" line to commits or PRs.
- Push only to `claude/*` branches. Never push to `main` or `master`.
