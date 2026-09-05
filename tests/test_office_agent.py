from pathlib import Path

from office_copilot_agent.agent import OfficeAgent


def test_add_task_and_summary() -> None:
    agent = OfficeAgent(name="Office Copilot Agent")
    agent.add_task("Prepare quarterly budget", due="2026-09-20", priority="high")
    agent.add_task("Send meeting notes", priority="low")

    assert len(agent.list_tasks()) == 2
    assert "quarterly budget" in agent.summary().lower()
    assert agent.list_tasks(only_pending=True)[0].status == "pending"


def test_create_excel_report(tmp_path: Path) -> None:
    agent = OfficeAgent()
    agent.add_task("Review invoices")

    arquivo = tmp_path / "office_report.xlsx"
    saved = agent.save_task_report(arquivo)

    assert saved.exists()
    assert saved.suffix == ".xlsx"


def test_draft_email_generation() -> None:
    agent = OfficeAgent()
    message = agent.draft_email("team@example.com", "Weekly update", "All tasks are on track.")

    assert message["To"] == "team@example.com"
    assert message["Subject"] == "Weekly update"
    assert "All tasks are on track." in message.get_body(preferencelist=("plain",)).get_content()
