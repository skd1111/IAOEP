"""IAOEP Python SDK — declarative OTLP tracing for AI agents.

Quick start:

```python
import os
from iaoep_sdk import observed_agent, observed_llm_call, observed_tool

@observed_agent(name="customer-service", skill="general")
def chat(query: str) -> str:
    return call_llm(query)

@observed_llm_call(system="openai", model_param="model")
def call_llm(model: str, query: str) -> str:
    # actual LLM call
    return "response"

@observed_tool(name="get_current_time")
def get_time() -> str:
    from datetime import datetime
    return datetime.now().isoformat()
```

Configuration via environment variables:

- IAOEP_EXPORTER_OTLP_ENDPOINT (default: http://localhost:4318)
- IAOEP_SERVICE_NAME (required)
- IAOEP_TENANT_ID (default: default)
- IAOEP_SAMPLING_RATIO (default: 1.0)
"""

from iaoep_sdk.config import configure, get_tracer
from iaoep_sdk.decorators import (
    observed_agent,
    observed_llm_call,
    observed_tool,
)
from iaoep_sdk.patch_langchain import patch_langchain
from iaoep_sdk.ab_test import (
    ab_test,
    ab_test_context,
    ABTestContext,
    ABTestClient,
)

__version__ = "0.4.0"

__all__ = [
    "configure",
    "get_tracer",
    "observed_agent",
    "observed_llm_call",
    "observed_tool",
    "patch_langchain",
    "ab_test",
    "ab_test_context",
    "ABTestContext",
    "ABTestClient",
]
