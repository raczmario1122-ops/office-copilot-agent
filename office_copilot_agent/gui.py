from __future__ import annotations

import tkinter as tk
from tkinter import ttk

from .agent import OfficeAgent


class OfficeApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Office Copilot Agent")
        self.geometry("980x620")
        self.agent = OfficeAgent()

        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=12, pady=12)

        self.tasks_frame = ttk.Frame(self.notebook)
        self.email_frame = ttk.Frame(self.notebook)
        self.chat_frame = ttk.Frame(self.notebook)

        self.notebook.add(self.tasks_frame, text="Tasks")
        self.notebook.add(self.email_frame, text="Email")
        self.notebook.add(self.chat_frame, text="Chat")

        self._build_tasks_view()
        self._build_email_view()
        self._build_chat_view()

    def _build_tasks_view(self) -> None:
        ttk.Label(self.tasks_frame, text="Task Title").grid(row=0, column=0, padx=8, pady=8, sticky="w")
        self.task_title = ttk.Entry(self.tasks_frame, width=40)
        self.task_title.grid(row=0, column=1, padx=8, pady=8, sticky="ew")

        ttk.Label(self.tasks_frame, text="Due date").grid(row=1, column=0, padx=8, pady=8, sticky="w")
        self.task_due = ttk.Entry(self.tasks_frame, width=25)
        self.task_due.grid(row=1, column=1, padx=8, pady=8, sticky="w")

        ttk.Label(self.tasks_frame, text="Priority").grid(row=2, column=0, padx=8, pady=8, sticky="w")
        self.task_priority = ttk.Combobox(self.tasks_frame, values=["low", "medium", "high"], state="readonly", width=15)
        self.task_priority.set("medium")
        self.task_priority.grid(row=2, column=1, padx=8, pady=8, sticky="w")

        ttk.Button(self.tasks_frame, text="Add Task", command=self.add_task).grid(row=3, column=0, padx=8, pady=10, sticky="w")
        ttk.Button(self.tasks_frame, text="Mark Done", command=self.mark_done).grid(row=3, column=1, padx=8, pady=10, sticky="w")
        ttk.Button(self.tasks_frame, text="Generate Excel Report", command=self.generate_report).grid(row=4, column=0, columnspan=2, padx=8, pady=10, sticky="w")

        self.task_list = tk.Text(self.tasks_frame, height=16, width=70)
        self.task_list.grid(row=0, column=2, rowspan=8, padx=12, pady=8, sticky="nsew")
        self.tasks_frame.columnconfigure(2, weight=1)
        self.tasks_frame.rowconfigure(7, weight=1)
        self.refresh_tasks()

    def _build_email_view(self) -> None:
        fields = [
            ("To", "email_to"),
            ("Subject", "email_subject"),
            ("Sender", "email_sender"),
        ]

        self.email_fields = {}
        for row, (label_text, name) in enumerate(fields):
            ttk.Label(self.email_frame, text=label_text).grid(row=row, column=0, padx=8, pady=8, sticky="w")
            entry = ttk.Entry(self.email_frame, width=60)
            entry.grid(row=row, column=1, padx=8, pady=8, sticky="ew")
            self.email_fields[name] = entry

        ttk.Label(self.email_frame, text="Body").grid(row=3, column=0, padx=8, pady=8, sticky="nw")
        self.email_body = tk.Text(self.email_frame, height=12, width=60)
        self.email_body.grid(row=3, column=1, padx=8, pady=8, sticky="nsew")

        ttk.Button(self.email_frame, text="Draft Email", command=self.draft_email).grid(row=4, column=0, columnspan=2, padx=8, pady=8, sticky="w")
        self.email_frame.columnconfigure(1, weight=1)
        self.email_frame.rowconfigure(3, weight=1)

    def _build_chat_view(self) -> None:
        self.chat_history = tk.Text(self.chat_frame, height=18, width=90, state="disabled")
        self.chat_history.pack(fill="both", expand=True, padx=10, pady=(10, 4))

        prompt_row = ttk.Frame(self.chat_frame)
        prompt_row.pack(fill="x", padx=10, pady=(0, 12))
        self.chat_input = ttk.Entry(prompt_row)
        self.chat_input.pack(side="left", fill="x", expand=True)
        ttk.Button(prompt_row, text="Send", command=self.send_chat).pack(side="left", padx=(8, 0))

        self.append_chat("Assistant", "Hello! Ask me what this app needs or request a task, email, or report.")

    def append_chat(self, sender: str, message: str) -> None:
        self.chat_history.configure(state="normal")
        self.chat_history.insert("end", f"{sender}: {message}\n\n")
        self.chat_history.configure(state="disabled")
        self.chat_history.see("end")

    def refresh_tasks(self) -> None:
        tasks = self.agent.list_tasks()
        self.task_list.delete("1.0", "end")
        if not tasks:
            self.task_list.insert("end", "No tasks yet.\n")
            return
        for task in tasks:
            self.task_list.insert("end", f"- {task.title} | {task.priority} | {task.status} | {task.due or 'No due date'}\n")

    def add_task(self) -> None:
        title = self.task_title.get().strip()
        if not title:
            self.append_chat("Assistant", "Please enter a task title.")
            return
        due = self.task_due.get().strip() or None
        priority = self.task_priority.get() or "medium"
        self.agent.add_task(title, due=due, priority=priority)
        self.task_title.delete(0, "end")
        self.task_due.delete(0, "end")
        self.refresh_tasks()
        self.append_chat("Assistant", f"Added task: {title}")

    def mark_done(self) -> None:
        title = self.task_title.get().strip()
        if not title:
            self.append_chat("Assistant", "Enter a task title first to mark it complete.")
            return
        task = self.agent.mark_done(title)
        self.refresh_tasks()
        if task:
            self.append_chat("Assistant", f"Marked complete: {task.title}")
        else:
            self.append_chat("Assistant", f"Task not found: {title}")

    def generate_report(self) -> None:
        path = "office_report.xlsx"
        self.agent.save_task_report(path)
        self.append_chat("Assistant", f"Excel report created at {path}")

    def draft_email(self) -> None:
        to = self.email_fields["email_to"].get().strip()
        subject = self.email_fields["email_subject"].get().strip()
        sender = self.email_fields["email_sender"].get().strip() or None
        body = self.email_body.get("1.0", "end").strip()
        if not to or not subject:
            self.append_chat("Assistant", "Please enter the email address and subject.")
            return
        message = self.agent.draft_email(to, subject, body, sender=sender)
        self.append_chat("Assistant", f"Drafted email for {to}: {message['Subject']}")

    def send_chat(self) -> None:
        prompt = self.chat_input.get().strip()
        self.chat_input.delete(0, "end")
        if not prompt:
            return
        self.append_chat("You", prompt)
        response = self.agent.respond_to_chat(prompt)
        self.append_chat("Assistant", response)


def main() -> None:
    app = OfficeApp()
    app.mainloop()


if __name__ == "__main__":
    main()
