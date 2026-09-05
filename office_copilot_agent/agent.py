from __future__ import annotations

from dataclasses import dataclass
from email.message import EmailMessage
from pathlib import Path
from typing import Iterable

try:
    from openpyxl import Workbook
except ImportError:  # pragma: no cover - handled in dependency installation.
    Workbook = None


@dataclass
class TaskItem:
    title: str
    due: str | None = None
    priority: str = "medium"
    status: str = "pending"

    def to_row(self) -> list[str]:
        return [self.title, self.due or "-", self.priority, self.status]


class OfficeAgent:
    """A small office automation agent for task management and business reporting."""

    def __init__(self, name: str = "Office Copilot Agent") -> None:
        self.name = name
        self.tasks: list[TaskItem] = []

    def add_task(self, title: str, due: str | None = None, priority: str = "medium") -> TaskItem:
        task = TaskItem(title=title.strip(), due=due, priority=priority.lower(), status="pending")
        if not task.title:
            raise ValueError("Task title cannot be empty.")
        self.tasks.append(task)
        return task

    def mark_done(self, title: str) -> TaskItem | None:
        for task in self.tasks:
            if task.title.lower() == title.lower():
                task.status = "done"
                return task
        return None

    def list_tasks(self, *, only_pending: bool = False) -> list[TaskItem]:
        if only_pending:
            return [task for task in self.tasks if task.status == "pending"]
        return list(self.tasks)

    def summary(self) -> str:
        pending = [task for task in self.tasks if task.status == "pending"]
        done = [task for task in self.tasks if task.status == "done"]
        top_tasks = ", ".join(task.title for task in self.tasks[:3]) if self.tasks else "no tasks"
        return (
            f"{self.name} summary: {len(pending)} pending tasks, {len(done)} completed tasks. "
            f"{len(self.tasks)} total tracked tasks. Top tasks: {top_tasks}."
        )

    def save_task_report(self, path: str | Path, sheet_name: str = "Tasks") -> Path:
        if Workbook is None:
            raise RuntimeError("openpyxl is required to generate Excel files. Install the project dependencies first.")

        output_path = Path(path)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        workbook = Workbook()
        worksheet = workbook.active
        worksheet.title = sheet_name
        worksheet.append(["Task", "Due", "Priority", "Status"])
        for task in self.tasks:
            worksheet.append(task.to_row())
        worksheet.freeze_panes = "A2"
        for column_cells in worksheet.columns:
            max_length = max(len(str(cell.value)) for cell in column_cells)
            worksheet.column_dimensions[column_cells[0].column_letter].width = max_length + 2

        workbook.save(output_path)
        return output_path

    def draft_email(self, to: str, subject: str, body: str, sender: str | None = None) -> EmailMessage:
        message = EmailMessage()
        message["To"] = to
        message["Subject"] = subject
        if sender:
            message["From"] = sender
        message.set_content(body)
        return message

    def send_email(
        self,
        to: str,
        subject: str,
        body: str,
        sender: str | None = None,
        smtp_server: str | None = None,
        smtp_port: int = 25,
        username: str | None = None,
        password: str | None = None,
    ) -> dict[str, str | int | bool]:
        message = self.draft_email(to=to, subject=subject, body=body, sender=sender)
        if smtp_server is None:
            return {
                "status": "draft_only",
                "to": to,
                "subject": subject,
                "message": message.as_string(),
            }

        import smtplib

        with smtplib.SMTP(smtp_server, smtp_port) as server:
            if username and password:
                server.login(username, password)
            server.send_message(message)

        return {
            "status": "sent",
            "to": to,
            "subject": subject,
            "smtp_server": smtp_server,
            "smtp_port": smtp_port,
        }

    @staticmethod
    def build_office_brief(tasks: Iterable[TaskItem]) -> str:
        task_list = list(tasks)
        if not task_list:
            return "No office tasks tracked right now."

        lines = ["Office brief:"]
        for task in task_list:
            lines.append(f"- {task.title} [{task.priority}] ({task.status})")
        return "\n".join(lines)


def _parse_args() -> None:
    import argparse

    parser = argparse.ArgumentParser(description="Office Copilot Agent")
    subparsers = parser.add_subparsers(dest="command", required=True)

    add_task = subparsers.add_parser("add-task", help="Add a task")
    add_task.add_argument("title")
    add_task.add_argument("--due")
    add_task.add_argument("--priority", default="medium")

    subparsers.add_parser("list-tasks", help="List tasks")

    report = subparsers.add_parser("report", help="Create an Excel worklist")
    report.add_argument("path", nargs="?", default="office_report.xlsx")

    email = subparsers.add_parser("draft-email", help="Draft an email")
    email.add_argument("to")
    email.add_argument("subject")
    email.add_argument("body")

    args = parser.parse_args()
    agent = OfficeAgent()

    if args.command == "add-task":
        task = agent.add_task(args.title, due=args.due, priority=args.priority)
        print(f"Added task: {task.title} ({task.priority})")
    elif args.command == "list-tasks":
        for task in agent.list_tasks():
            print(f"- {task.title} | {task.status} | {task.priority} | {task.due or 'No due date'}")
    elif args.command == "report":
        report_path = agent.save_task_report(args.path)
        print(f"Created report at {report_path}")
    elif args.command == "draft-email":
        message = agent.draft_email(args.to, args.subject, args.body)
        print(message.as_string())


if __name__ == "__main__":
    _parse_args()
