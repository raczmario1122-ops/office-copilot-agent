from office_copilot_agent.agent import OfficeAgent


def test_chat_response_for_needed_features() -> None:
    agent = OfficeAgent()

    response = agent.respond_to_chat("What do I need in this app?")

    assert "task tracking" in response.lower()
    assert "excel" in response.lower()
    assert "email" in response.lower()


def test_chat_response_for_task_request() -> None:
    agent = OfficeAgent()

    response = agent.respond_to_chat("Add task to review the budget")

    assert "Added task" in response
    assert len(agent.list_tasks()) == 1
