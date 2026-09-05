# office-copilot-agent

A lightweight office-work Copilot agent for managing tasks, generating Excel worklists, and drafting email updates.

## What it does

- Tracks office tasks with priorities and due dates
- Creates Excel spreadsheets for reporting and follow-up
- Drafts emails for internal communication
- Can be extended for Outlook/Gmail integrations and workflow automation

## Quick start

```bash
python -m pip install -e ".[develop]"
python -m office_copilot_agent add-task "Prepare quarterly budget" --due 2026-09-20 --priority high
python -m office_copilot_agent list-tasks
python -m office_copilot_agent report office_report.xlsx
python -m office_copilot_agent draft-email team@example.com "Weekly update" "All tasks are on track."
```

## Example Python usage

```python
from office_copilot_agent import OfficeAgent

agent = OfficeAgent()
agent.add_task("Review invoices", due="2026-09-18", priority="high")
agent.save_task_report("office_report.xlsx")
message = agent.draft_email("team@example.com", "Status update", "The desk is organized and the files are ready.")
print(message["Subject"])
```

## Roadmap

- Add Outlook and Gmail connectors
- Add document and spreadsheet templates for finance and HR workflows
- Add rich task orchestration for recurring office operations
