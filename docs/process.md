# How the work is organised

Work on Personal Site Engine goes through a small team of agents, each with one role. The main session is the
**orchestrator**: it starts the roles as subagents, passes the result of one to the next and does none of the work
itself. The roles are defined in `.claude/agents/`; the rules every agent follows are in [AGENTS.md](../AGENTS.md).

## The roles

| Role | File | Does | Does not |
|---|---|---|---|
| Product manager | `.claude/agents/pm.md` | Turns a task of the backlog into Goal, Acceptance criteria (checkboxes), Out of scope and Constraints | write code |
| Software engineer | `.claude/agents/software-engineer.md` | Implements one groomed task, writes the tests, commits locally | change the criteria, finish the task, push |
| QA engineer | `.claude/agents/qa-engineer.md` | Checks every criterion against the code and the test results and answers `PASS` or `FAIL` | change code |

## The backlog

[tasks.md](tasks.md) holds the open tasks, [tasks-done.md](tasks-done.md) the finished ones; a task keeps its number.
A task has a number and a title, then **Goal**, **Description** and **Acceptance criterion**. A *groomed* task
additionally has these four parts, which the product manager writes into the task:

```
Goal: what is true afterwards, in one sentence.
Acceptance criteria:
- [ ] one checkable statement per line, each one testable
Out of scope: what looks related but is not part of this task (each item becomes a task of its own).
Constraints: files, libraries and earlier decisions to keep, with references to docs/spec.md.
```

A task must fit into one session. If it does not, the product manager splits it and adds the parts to the backlog.

## The life of a task

1. The orchestrator takes the next open task of [tasks.md](tasks.md), following its "Suggested order".
2. The **product manager** grooms it.
3. The **software engineer** implements it against the criteria and runs `./mvnw -B verify`.
4. The **QA engineer** checks each criterion and answers with `PASS` or `FAIL` and the reason per criterion.
5. On `FAIL` the orchestrator returns the QA answer to the engineer; steps 3 and 4 repeat.
6. On `PASS` the orchestrator moves the task from `tasks.md` to `tasks-done.md` with its Status, Done in and Verified
   lines, and updates the documents the task touches.

Then the orchestrator reports to the owner and stops. Pushing is the owner's decision (see AGENTS.md).

## Rules

- Grooming is never skipped, also for a task that looks clear.
- The engineer never closes a task and never changes its criteria. If a criterion is wrong, the engineer says so and
  the product manager changes it.
- QA never edits code or tests. It reports what it found.
- After three rounds of engineer and QA without `PASS`, the orchestrator stops and asks the owner.
- Anything in the task that needs the server, GitHub or a secret goes to the owner; no agent does it on its own.
