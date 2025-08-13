# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JoyAgent-JDGenie is an open-source multi-agent system for task automation and intelligent processing. It's an end-to-end multi-agent product that can directly answer queries or solve tasks, such as generating comprehensive reports or presentations from user requests.

The system consists of four main components:
- **Backend (genie-backend)**: Java Spring Boot service managing agent orchestration (Port 8080)
- **Frontend (ui)**: React TypeScript web interface with real-time streaming (Port 3000)
- **Tools Service (genie-tool)**: Python FastAPI service providing specialized agent tools (Port 1601)
- **MCP Client (genie-client)**: Python service for Model Context Protocol integration (Port 8188)

## Architecture

### Multi-Agent System Design
- **Agent Types**: ReAct, Plan-and-Execute, Workflow, PromptFlow, and Summary agents
- **Tool System**: Pluggable tools including search, code interpreter, report generation, and file management
- **Workflow Engine**: DAG-based execution with parallel processing capabilities
- **PromptFlow Engine**: Markdown-based workflow definition with variable substitution
- **MCP Integration**: Support for Model Context Protocol tools

### Core Components
- **AgentContext**: Central context management for agent state and data flow
- **ToolCollection**: Dynamic tool registration and execution system
- **Memory System**: Cross-task memory management for similar tasks
- **Streaming Output**: Full-pipeline SSE streaming for real-time responses

## Development Commands

### Backend (Java/Maven)
```bash
# Build the backend
cd genie-backend && mvn clean package

# Run backend service (port 8080)
cd genie-backend && mvn spring-boot:run

# Run tests
cd genie-backend && mvn test
```

### Frontend (Node.js/Vite)
```bash
# Install dependencies
cd ui && pnpm install

# Development server (port 3000)
cd ui && pnpm dev

# Build for production
cd ui && pnpm build

# Lint code
cd ui && pnpm lint

# Fix linting issues
cd ui && pnpm fix
```

### Tools Service (Python/UV)
```bash
# Setup environment
cd genie-tool && uv sync

# Activate virtual environment
cd genie-tool && source .venv/bin/activate

# Run tools server (port 1601)
cd genie-tool && python server.py
```

### Docker Deployment
```bash
# Build and run all services
docker build -t genie:latest .
docker run -d -p 3000:3000 -p 8080:8080 -p 1601:1601 \
  -e OPENAI_BASE_URL="your_llm_url" \
  -e OPENAI_API_KEY="your_api_key" \
  --name genie-app genie:latest
```

### Quick Start Script
```bash
# Check dependencies and ports
./check_dep_port.sh

# Start all services (handles first-time setup automatically)
./Genie_start.sh
```

### First Time Setup
The startup script (`Genie_start.sh`) automatically handles first-time configuration:
- Validates LLM configuration in `application.yml` (checks for placeholders like `<input llm server here>`)
- Copies `.env_template` to `.env` in genie-tool directory and validates API keys
- Builds backend with `genie-backend/build.sh`
- Initializes tool service database using `genie_tool.db.db_engine`
- Creates virtual environments for Python services using `uv`
- Performs health checks on all service endpoints

### Running Individual Services
```bash
# Backend only
cd genie-backend && mvn spring-boot:run

# Frontend only  
cd ui && pnpm dev

# Tools service only
cd genie-tool && source .venv/bin/activate && python server.py

# MCP client only
cd genie-client && source .venv/bin/activate && python server.py
```

## Key Configuration Files

### Backend Configuration
- `genie-backend/src/main/resources/application.yml`: Main configuration including LLM settings, agent prompts, and tool configurations
- `genie-backend/pom.xml`: Java dependencies and build configuration  
- `genie-backend/build.sh`: Backend build script with Maven configuration
- `genie-backend/start.sh`: Backend startup script

### Frontend Configuration  
- `ui/package.json`: Node.js dependencies and scripts
- `ui/vite.config.ts`: Vite build configuration
- `ui/tailwind.config.js`: Tailwind CSS configuration
- `ui/start.sh`: Frontend startup script

### Tools Configuration
- `genie-tool/pyproject.toml`: Python dependencies
- `genie-tool/.env_template`: Environment variables template (requires OPENAI_API_KEY, OPENAI_BASE_URL, SERPER_SEARCH_API_KEY)
- `genie-tool/server.py`: FastAPI server for tool services
- `genie-tool/start.sh`: Tools service startup script

### MCP Client Configuration
- `genie-client/pyproject.toml`: Python dependencies for MCP client
- `genie-client/server.py`: MCP client server implementation  
- `genie-client/start.sh`: MCP client startup script

### Workflow Configuration
- `genie-backend/src/main/resources/workflows.yml`: Predefined workflow templates including data-analysis, research, parallel-analysis, problem-solving, and simple-tool-flow

## Agent Implementation Patterns

### Creating Custom Agents
Extend `BaseAgent` class in `com.jd.genie.agent.agent.BaseAgent`:
- Implement abstract `step()` method for agent logic
- Use `updateMemory()` for conversation state management  
- Leverage `executeTool()` for tool interactions
- Register in `AgentHandlerConfig` at `genie-backend/src/main/java/com/jd/genie/handler/AgentHandlerConfig.java`

### Agent Response Handlers
Each agent type has a dedicated response handler in `com.jd.genie.handler`:
- `ReactAgentResponseHandler`: ReAct agent streaming responses
- `PlanSolveAgentResponseHandler`: Plan-and-Execute agent responses  
- `WorkflowAgentResponseHandler`: Workflow-based agent responses
- `PromptFlowAgentResponseHandler`: PromptFlow agent responses with metadata
- All extend `BaseAgentResponseHandler` for consistent response formatting

### Adding Custom Tools
Implement `BaseTool` interface in `com.jd.genie.agent.tool.BaseTool`:
```java
public interface BaseTool {
    String getName();           // Tool identifier
    String getDescription();    // Tool description for LLM
    Map<String, Object> toParams(); // Tool parameters schema
    Object execute(Object input);   // Tool execution logic
}
```

Register tools in `GenieController.buildToolCollection()` at `genie-backend/src/main/java/com/jd/genie/controller/GenieController.java:126`:
```java
YourCustomTool customTool = new YourCustomTool();
customTool.setAgentContext(agentContext);
toolCollection.addTool(customTool);
```

### Tool System Architecture
- **ToolCollection**: Central registry managing all available tools (`com.jd.genie.agent.tool.ToolCollection`)
- **Tool Categories**: Common tools (search, file, code interpreter), MCP tools (external integrations)
- **Tool Context**: All tools receive `AgentContext` for shared state management
- **Tool Execution**: Supports both synchronous and parallel execution via `executeTools()`

### MCP Tool Integration
Add MCP server URLs in `application.yml`:
```yaml
mcp_server_url: "http://server1:port1/sse,http://server2:port2/sse"
```

### Workflow System Usage
Workflows are defined in `workflows.yml` with the following structure:
- **Step Types**: `llm_call`, `tool_call`, `parallel`, `sequential`
- **Dependencies**: Use `dependencies: ["step-id"]` for execution order
- **Variables**: Template variables with `${variable_name}` syntax
- **Retry Policies**: Support FIXED_DELAY and EXPONENTIAL backoff strategies
- **Parallel Execution**: Use `type: "parallel"` with `maxConcurrency` control

### PromptFlow Agent System
PromptFlow enables Markdown-based workflow definitions for non-technical users:

**Architecture Components:**
- **MarkdownFlowParser**: Parses Markdown workflow definitions (`com.jd.genie.agent.promptflow.parser`)
- **FlowEngine**: Executes parsed workflows with node-based processing (`com.jd.genie.agent.promptflow.engine`)
- **Node Types**: PromptNode, ToolNode, MarkdownNode, ControlNode (`com.jd.genie.agent.promptflow.model`)
- **Variable System**: `{{variable_name}}` syntax for dynamic content substitution

**Markdown Workflow Syntax:**
```markdown
# Workflow Name
## Configuration
- Model: gpt-4
- Author: {{author}}

## Steps
1. **Step Name** [prompt]
   > Your prompt content here with {{variables}}

2. **Tool Call** [tool:tool_name]
   - param: value
   - query: {{user_input}}

3. **Conditional Logic** [control:condition]
   - condition: {{step_1_result}} == "success"
   - true_node: step_4
   - false_node: step_5
```

**Usage Patterns:**
- Use `workflowDefinition` field in AgentRequest for inline Markdown content
- Use `workflowVariables` field for JSON-encoded variable mappings
- Templates available in `flows/templates/` directory for common patterns
- Built-in variables: `user_input`, `current_date`, `current_time`

## Frontend Architecture

### Component Structure
- **ChatView**: Main chat interface with SSE streaming
- **ActionView**: Task execution visualization panel
- **GeneralInput**: Multi-modal input component with file upload
- **Dialogue**: Message display with markdown and file rendering

### State Management
- Uses React hooks for local state management
- SSE for real-time backend communication via `EventSource` in `ui/src/utils/querySSE.ts`
- Task and plan state managed through `handleTaskData()` and `combineData()` in `ui/src/components/ChatView/index.tsx`
- Message streaming handled by `SseEmitterUTF8` on backend at `genie-backend/src/main/java/com/jd/genie/util/SseEmitterUTF8.java`

### Agent Mode Selection
Support for different agent types:
- `react`: ReAct agent with Reasoning-Acting-Observing pattern (default)
- `plan_solve`: Plan-and-Execute agent with planning and execution phases
- `workflow`: Workflow-based agent using predefined DAG templates from `workflows.yml`
- `prompt_flow`: PromptFlow agent using Markdown-based workflow definitions

## Testing and Quality

### Backend Testing
```bash
# Run all tests
cd genie-backend && mvn test

# Run specific test class
cd genie-backend && mvn test -Dtest=GenieTest

# Run specific test method
cd genie-backend && mvn test -Dtest=GenieTest#specificTestMethod

# Run tests with specific profile
cd genie-backend && mvn test -Dspring.profiles.active=test

# Skip tests during build
cd genie-backend && mvn clean package -DskipTests

# Compile only (useful for checking compilation errors)
cd genie-backend && mvn compile

# Clean and compile (fresh build)
cd genie-backend && mvn clean compile
```
- Unit tests in `genie-backend/src/test/java/`
- Integration tests for agent workflows
- Tool execution validation
- Spring Boot test framework integration

### Frontend Testing
```bash
# Lint code
cd ui && pnpm lint

# Fix linting issues automatically
cd ui && pnpm fix
```
- TypeScript strict mode enabled in `ui/tsconfig.json`
- ESLint with React and TypeScript rules in `ui/eslint.config.js`
- Prettier code formatting integration
- Component testing for key interfaces

## Development Guidelines

### Backend Development
- Follow Spring Boot conventions
- Use Lombok for reducing boilerplate
- Implement proper error handling with logging
- Maintain agent state consistency
- Use async execution for tool calls

### Frontend Development  
- Follow React functional component patterns
- Use TypeScript for type safety
- Implement proper error boundaries
- Handle SSE connection lifecycle
- Maintain responsive design principles

### Tool Development
- Follow the BaseTool interface contract (`implements BaseTool` not `extends`)
- Add `setAgentContext(AgentContext agentContext)` method for tool context access
- Implement proper parameter validation
- Handle execution timeouts gracefully
- Provide detailed error messages
- Support both sync and async operations
- Register tools in ToolCollection via `addTool()` method

## Performance Considerations

- **Agent Execution**: Uses thread pools for concurrent processing in `com.jd.genie.agent.util.ThreadUtil`
- **Tool Parallelization**: Tool calls can be executed in parallel using `executeTools()` method
- **Workflow Concurrency**: Workflow steps support `maxConcurrency` control for parallel execution
- **Frontend Optimization**: Uses React.memo and useMemoizedFn for performance optimization
- **SSE Reliability**: Connections include heartbeat mechanism via `SseEmitterUTF8` for reliability
- **Database Operations**: SQLite with connection pooling for tool service data storage
- **Memory Management**: Cross-task memory system prevents redundant processing of similar tasks

## System Architecture Insights

### Agent Lifecycle Management
- **Agent States**: IDLE → RUNNING → FINISHED/ERROR
- **Memory Context**: Each agent maintains conversation state via `updateMemory()`
- **Tool Integration**: Unified tool interface through `ToolCollection` with native and MCP tool support
- **Response Streaming**: Full-pipeline SSE from agent execution to frontend display

### Configuration Hierarchy
- **LLM Settings**: Multiple model configurations with fallback support in `application.yml`
- **Agent Prompts**: Customizable system prompts for each agent type (react, planner, executor)
- **Tool Parameters**: Dynamic parameter schemas defined in tool configuration
- **Workflow Templates**: Reusable workflow definitions with variable substitution

### Development Workflow Patterns
- **Hot Reload**: Development servers support live code updates
- **Service Health**: Startup script includes comprehensive health checks for all services
- **Error Recovery**: Built-in retry mechanisms with exponential backoff for tool calls
- **Logging**: Structured logging across all services with configurable levels

## Debugging and Troubleshooting

### Common Compilation Issues
- **"此处不需要接口" (Interface not needed)**: Use `implements BaseTool` instead of `extends BaseTool`
- **Missing method errors**: Check Lombok `@Data` annotations generate required getters/setters
- **Printer interface issues**: Use `printer.send(messageType, content)` instead of `printer.print()`
- **AgentContext missing methods**: Use field names directly, getters generated by Lombok

### SSE Streaming Debug
- Check `SseEmitterUTF8` implementation at `genie-backend/src/main/java/com/jd/genie/util/SseEmitterUTF8.java`
- Message types: `prompt_flow_step`, `prompt_flow_progress`, `prompt_flow_result`, `workflow_*`
- Frontend message handling in `ui/src/utils/chat.ts` with `combineData()` function

### Agent Registration
- All agents must be registered in `AgentHandlerConfig`
- Response handlers must extend `BaseAgentResponseHandler`
- Agent types defined in `com.jd.genie.agent.enums.AgentType` enum
- Frontend agent modes in `ui/src/utils/constants.ts` agentModeList

### Tool Integration Issues
- Tools must implement `BaseTool` interface with all required methods
- Use `toolCollection.addTool(tool)` for registration
- MCP tools registered via `addMcpTool()` method
- Tool execution context passed through `AgentContext`

## Important Instructions

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.

## Security Notes

- Input validation on all API endpoints  
- Sensitive information filtered from logs
- MCP server connections validated
- File upload restrictions enforced
- Agent execution sandboxed where possible