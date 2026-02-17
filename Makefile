# BATbern Platform - Unified Build System
# =========================================
# Makefile for building and testing all components of the polyglot monorepo
#
# Project Structure:
#   - Java/Gradle: shared-kernel, api-gateway, 5x domain services
#   - TypeScript/Node: infrastructure (CDK), web-frontend (React)
#   - Swift/Xcode: BATbern-watch (watchOS companion app)
#
# Usage: make <target>
# Run 'make help' to see all available targets

.DEFAULT_GOAL := help
.PHONY: help install build test lint clean docker-up docker-down docker-build verify ci-build ci-test check-outdated update-deps audit-security all build-watch test-watch clean-watch watch-generate-types

# ═══════════════════════════════════════════════════════════
# HELP & DOCUMENTATION
# ═══════════════════════════════════════════════════════════

help: ## Show this help message
	@echo "╔════════════════════════════════════════════════════════════╗"
	@echo "║        BATbern Platform - Build System Commands           ║"
	@echo "╚════════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "📦 Setup & Installation:"
	@echo "  make install          - Install all dependencies (Java + Node)"
	@echo "  make install-java     - Install Java dependencies (Gradle)"
	@echo "  make install-node     - Install Node.js dependencies (npm)"
	@echo ""
	@echo "🔨 Build:"
	@echo "  make build            - Build all projects"
	@echo "  make build-java       - Build Java projects only"
	@echo "  make build-node       - Build Node.js projects only"
	@echo "  make build-watch      - Build watchOS app only"
	@echo ""
	@echo "🧪 Testing:"
	@echo "  make test             - Run all tests (unit + integration, coverage report, requires Docker)"
	@echo "  make test-java        - Run Java tests (unit + integration)"
	@echo "  make test-node        - Run Node.js tests (unit tests only)"
	@echo "  make test-watch       - Run watchOS app tests"
	@echo "  Note: Integration tests use Testcontainers (requires Docker) but AWS credentials NOT needed"
	@echo ""
	@echo "✨ Code Quality:"
	@echo "  make lint             - Run all linters"
	@echo "  make lint-java        - Run Java linters (checkstyle)"
	@echo "  make lint-node        - Run Node.js linters (eslint)"
	@echo "  make format           - Format all code"
	@echo "  make format-check     - Check code formatting"
	@echo "  make audit-security   - Run security audits"
	@echo ""
	@echo "🧹 Cleanup:"
	@echo "  make clean            - Clean all build artifacts"
	@echo "  make clean-java       - Clean Java build artifacts"
	@echo "  make clean-node       - Clean Node.js build artifacts"
	@echo "  make clean-watch      - Clean watchOS build artifacts"
	@echo ""
	@echo "🐳 Docker:"
	@echo "  make docker-up        - Start all services with Docker Compose"
	@echo "  make docker-down      - Stop all Docker services"
	@echo "  make docker-build     - Build Docker images for all services"
	@echo ""
	@echo "⚡ Native Development (No Docker - 60-70% less resources):"
	@echo "  make dev-native-up            - Start all services natively"
	@echo "  make dev-native-down          - Stop all native services"
	@echo "  make dev-native-status        - Show service status"
	@echo "  make dev-native-logs          - Tail all service logs"
	@echo "  make dev-native-restart       - Restart all services"
	@echo ""
	@echo "🔀 Parallel Instances (Multiple Dev Environments):"
	@echo "  make dev-native-up-instance BASE_PORT=9000      - Start instance 2"
	@echo "  make dev-native-down-instance BASE_PORT=9000    - Stop instance 2"
	@echo "  make dev-native-list                            - List all instances"
	@echo "  make dev-native-status-all                      - Status of all instances"
	@echo "  make dev-native-status-instance BASE_PORT=9000  - Status of instance"
	@echo "  make dev-native-logs-instance BASE_PORT=9000    - Logs from instance"
	@echo ""
	@echo "🚀 CI/CD:"
	@echo "  make ci-build         - Full CI build (clean + build + test)"
	@echo "  make ci-test          - Run all CI tests"
	@echo ""
	@echo "📊 Dependency Management:"
	@echo "  make check-outdated   - Check for outdated dependencies"
	@echo "  make update-deps      - Update safe dependencies (patch/minor)"
	@echo ""
	@echo "🔍 Utilities:"
	@echo "  make verify              - Pre-commit verification (lint + test)"
	@echo "  make all                 - Clean + Install + Build + Test"
	@echo "  make watch-generate-types - Generate Swift types from OpenAPI spec"
	@echo ""

# ═══════════════════════════════════════════════════════════
# INSTALLATION
# ═══════════════════════════════════════════════════════════

install: install-java install-node ## Install all dependencies

install-java: ## Install Java/Gradle dependencies
	@echo "📦 Installing Java dependencies..."
	@echo "→ Building shared-kernel..."
	@./gradlew :shared-kernel:build :shared-kernel:publishToMavenLocal -x test
	@echo "✓ Java dependencies installed"

install-node: ## Install Node.js dependencies
	@echo "📦 Installing Node.js dependencies..."
	@echo "→ Installing infrastructure dependencies..."
	@cd infrastructure && npm ci
	@echo "→ Installing web-frontend dependencies..."
	@cd web-frontend && npm ci
	@echo "✓ Node.js dependencies installed"

# ═══════════════════════════════════════════════════════════
# BUILD
# ═══════════════════════════════════════════════════════════

build: build-java build-node ## Build all projects

build-java: ## Build all Java/Gradle projects
	@echo "🔨 Building Java projects..."
	@echo "→ Building with Gradle (shared-kernel, api-gateway, services)..."
	@echo "→ Running checkstyle validation..."
	@./gradlew build compileTestJava checkstyleMain checkstyleTest -x test --parallel
	@echo "✓ Java build complete"

build-node: ## Build all Node.js projects
	@echo "🔨 Building Node.js projects..."
	@echo "→ Linting web-frontend..."
	@cd web-frontend && npm run lint
	@echo "→ Building infrastructure..."
	@cd infrastructure && npm run build
	@echo "→ Building web-frontend..."
	@cd web-frontend && npm run build
	@echo "✓ Node.js build complete"

build-watch: ## Build watchOS app (BATbern-watch)
	@echo "🔨 Building watchOS app..."
	@echo "→ Building BATbern-watch for watchOS Simulator..."
	@xcodebuild -scheme "BATbern-watch Watch App" \
		-destination 'platform=watchOS Simulator,name=Apple Watch Series 11 (46mm)' \
		-project apps/BATbern-watch/BATbern-watch.xcodeproj \
		clean build \
		| grep -E '^(Build|▸|⚠️|❌|error:|warning:)' || true
	@echo "✓ watchOS build complete"

# ═══════════════════════════════════════════════════════════
# TESTING
# ═══════════════════════════════════════════════════════════

test: test-java test-node ## Run all tests with coverage verification

test-java: ## Run Java tests with coverage verification (requires Docker)
	@echo "🧪 Running Java tests with coverage verification..."
	@echo "  → Running unit + integration tests (Testcontainers PostgreSQL)"
	@echo "  → AWS services are mocked (no credentials needed)"
	@./gradlew test jacocoTestReport jacocoTestCoverageVerification
	@echo "✓ Java tests complete with coverage verified"
	@echo ""
	@echo "📊 Java Coverage: build/reports/jacoco/test/html/index.html"

test-node: ## Run Node.js tests with coverage
	@echo "🧪 Running Node.js tests with coverage..."
	@echo "→ Testing infrastructure..."
	@cd infrastructure && npm test -- --coverage || true
	@echo "→ Testing web-frontend..."
	@cd web-frontend && npm run test:coverage
	@echo "✓ Node.js tests complete"
	@echo ""
	@echo "📊 Coverage Reports:"
	@echo "  CDK:      infrastructure/coverage/index.html"
	@echo "  Frontend: web-frontend/coverage/index.html"
	@echo "💡 To run E2E tests: cd web-frontend && npm run test:e2e"

test-watch: ## Run watchOS app tests
	@echo "🧪 Running watchOS app tests..."
	@echo "→ Running unit tests for BATbern-watch..."
	@xcodebuild test -scheme "BATbern-watch Watch App" \
		-destination 'platform=watchOS Simulator,name=Apple Watch Series 11 (46mm)' \
		-project apps/BATbern-watch/BATbern-watch.xcodeproj \
		| grep -E '^(Test|▸|⚠️|❌|✔|✘|◇|error:|warning:|Executed)' || true
	@echo "✓ watchOS tests complete"

# ═══════════════════════════════════════════════════════════
# CODE QUALITY
# ═══════════════════════════════════════════════════════════

lint: lint-java lint-node ## Run all linters

lint-java: ## Run Java linters
	@echo "✨ Running Java linters..."
	@./gradlew check compileTestJava -x test
	@echo "✓ Java linting complete"

lint-node: ## Run Node.js linters
	@echo "✨ Running Node.js linters..."
	@echo "→ Linting web-frontend..."
	@cd web-frontend && npm run lint
	@echo "✓ Node.js linting complete"

format: ## Format all code
	@echo "✨ Formatting code..."
	@echo "→ Formatting web-frontend..."
	@cd web-frontend && npm run format
	@echo "✓ Code formatting complete"

format-check: ## Check code formatting
	@echo "✨ Checking code formatting..."
	@echo "→ Checking web-frontend..."
	@cd web-frontend && npm run format:check
	@echo "✓ Format check complete"

# ═══════════════════════════════════════════════════════════
# CLEANUP
# ═══════════════════════════════════════════════════════════

clean: clean-java clean-node clean-watch ## Clean all build artifacts

clean-java: ## Clean Java build artifacts
	@echo "🧹 Cleaning Java build artifacts..."
	@./gradlew clean
	@echo "✓ Java clean complete"

clean-node: ## Clean Node.js build artifacts
	@echo "🧹 Cleaning Node.js build artifacts..."
	@rm -rf infrastructure/node_modules infrastructure/cdk.out
	@rm -rf web-frontend/node_modules web-frontend/dist web-frontend/coverage
	@echo "✓ Node.js clean complete"

clean-watch: ## Clean watchOS build artifacts
	@echo "🧹 Cleaning watchOS build artifacts..."
	@xcodebuild clean -scheme "BATbern-watch Watch App" \
		-project apps/BATbern-watch/BATbern-watch.xcodeproj \
		| grep -E '^(Clean|▸|⚠️|❌)' || true
	@echo "✓ watchOS clean complete"

# ═══════════════════════════════════════════════════════════
# DOCKER
# ═══════════════════════════════════════════════════════════

docker-up: ## Start all services with Docker Compose (includes DB tunnel)
	@echo "🐳 Starting Docker services..."
	@echo ""
	@echo "→ Step 1: Starting database tunnel..."
	@if pgrep -f "start-db-tunnel.sh" > /dev/null; then \
		echo "  ⚠️  Tunnel already running (PID: $$(pgrep -f 'start-db-tunnel.sh'))"; \
	else \
		./scripts/dev/start-db-tunnel.sh > /tmp/db-tunnel.log 2>&1 & \
		echo $$! > /tmp/db-tunnel.pid; \
		echo "  ✓ Tunnel started (PID: $$!)"; \
		echo "  📝 Logs: /tmp/db-tunnel.log"; \
		sleep 5; \
	fi
	@echo ""
	@echo "→ Step 2: Starting Docker containers..."
	@docker-compose up -d
	@echo ""
	@echo "✓ All services started"
	@echo ""
	@echo "Services running at:"
	@echo "  API Gateway:   http://localhost:8080"
	@echo "  Web Frontend:  http://localhost:3000"
	@echo ""
	@echo "💡 Database tunnel: localhost:5432 → AWS RDS"
	@echo "   View tunnel logs: tail -f /tmp/db-tunnel.log"

docker-down: ## Stop Docker services (keeps DB tunnel running)
	@echo "🐳 Stopping Docker services..."
	@docker-compose down
	@echo "✓ Docker services stopped"
	@echo ""
	@echo "💡 Database tunnel still running"
	@echo "   To stop tunnel: make docker-tunnel-stop"

docker-tunnel-stop: ## Stop database tunnel
	@echo "🔒 Stopping database tunnel..."
	@if [ -f /tmp/db-tunnel.pid ]; then \
		PID=$$(cat /tmp/db-tunnel.pid); \
		if ps -p $$PID > /dev/null 2>&1; then \
			kill $$PID && echo "  ✓ Tunnel stopped (PID: $$PID)"; \
		else \
			echo "  ⚠️  Tunnel process not found"; \
		fi; \
		rm -f /tmp/db-tunnel.pid; \
	elif pgrep -f "start-db-tunnel.sh" > /dev/null; then \
		pkill -f "start-db-tunnel.sh" && echo "  ✓ Tunnel stopped"; \
	else \
		echo "  ⚠️  No tunnel running"; \
	fi

docker-tunnel-logs: ## Show database tunnel logs
	@echo "📝 Database tunnel logs:"
	@echo ""
	@tail -50 /tmp/db-tunnel.log 2>/dev/null || echo "No logs found. Start tunnel with 'make docker-up'"

docker-restart: docker-down docker-up ## Restart all Docker services

docker-build: ## Build Docker images for all services
	@echo "🐳 Building Docker images..."
	@./scripts/build-and-push-services.sh local
	@echo "✓ Docker images built"

# ═══════════════════════════════════════════════════════════
# NATIVE DEVELOPMENT (No Docker)
# ═══════════════════════════════════════════════════════════

dev-native-up: ## Start all services natively (without Docker) - 60-70% less resources
	@echo "🚀 Starting services natively (no Docker)..."
	@./scripts/dev/start-all-native.sh

dev-native-down: ## Stop all native services
	@echo "🛑 Stopping native services..."
	@./scripts/dev/stop-all-native.sh

dev-native-status: ## Show status of native services
	@./scripts/dev/status-native.sh

dev-native-logs: ## Tail logs from all native services
	@./scripts/dev/logs-native.sh

dev-native-logs-service: ## Tail logs from specific service (use SERVICE=name)
	@./scripts/dev/logs-native.sh $(SERVICE)

dev-native-restart: ## Restart all native services
	@./scripts/dev/restart-native.sh

dev-native-restart-service: ## Restart specific native service (use SERVICE=name)
	@./scripts/dev/restart-native.sh $(SERVICE)

dev-seed-data: ## Seed test data into local database (event + speakers)
	@echo "🌱 Seeding test data into local database..."
	@docker exec batbern-dev-postgres psql -U postgres -d batbern_development -f /dev/stdin < scripts/dev/seed-test-data.sql
	@echo "📄 Uploading seed material PDFs to MinIO..."
	@scripts/dev/seed-materials.sh 2>/dev/null || echo "⚠️  MinIO material upload skipped (mc not installed or MinIO not running)"
	@echo "✓ Test data seeded"

# ═══════════════════════════════════════════════════════════
# PARALLEL INSTANCE SUPPORT (Multiple Local Dev Environments)
# ═══════════════════════════════════════════════════════════

dev-native-up-instance: ## Start specific instance (use BASE_PORT=9000 for instance 2)
	@if [ -z "$(BASE_PORT)" ]; then \
		echo "❌ Error: BASE_PORT parameter required"; \
		echo "Usage: make dev-native-up-instance BASE_PORT=9000"; \
		echo ""; \
		echo "Recommended port ranges:"; \
		echo "  BASE_PORT=8000 (default, services 8000-8005, frontend 8100)"; \
		echo "  BASE_PORT=9000 (services 9000-9005, frontend 9100)"; \
		echo "  BASE_PORT=9500 (services 9500-9505, frontend 9600)"; \
		echo "  Any BASE_PORT value is supported"; \
		exit 1; \
	fi
	@echo "🚀 Starting instance with BASE_PORT=$(BASE_PORT)..."
	@BASE_PORT=$(BASE_PORT) ./scripts/dev/start-all-native.sh

dev-native-down-instance: ## Stop specific instance (use BASE_PORT=9000)
	@if [ -z "$(BASE_PORT)" ]; then \
		echo "❌ Error: BASE_PORT parameter required"; \
		echo "Usage: make dev-native-down-instance BASE_PORT=9000"; \
		exit 1; \
	fi
	@echo "🛑 Stopping instance with BASE_PORT=$(BASE_PORT)..."
	@BASE_PORT=$(BASE_PORT) ./scripts/dev/stop-all-native.sh

dev-native-list: ## List all running instances
	@./scripts/dev/list-instances.sh

dev-native-status-all: ## Show status of all instances
	@./scripts/dev/status-all-instances.sh

dev-native-status-instance: ## Show status of specific instance (use BASE_PORT=9000)
	@if [ -z "$(BASE_PORT)" ]; then \
		echo "❌ Error: BASE_PORT parameter required"; \
		echo "Usage: make dev-native-status-instance BASE_PORT=9000"; \
		exit 1; \
	fi
	@BASE_PORT=$(BASE_PORT) ./scripts/dev/status-native.sh

dev-native-logs-instance: ## Tail logs from specific instance (use BASE_PORT=9000)
	@if [ -z "$(BASE_PORT)" ]; then \
		echo "❌ Error: BASE_PORT parameter required"; \
		echo "Usage: make dev-native-logs-instance BASE_PORT=9000"; \
		exit 1; \
	fi
	@BASE_PORT=$(BASE_PORT) ./scripts/dev/logs-native.sh

dev-native-restart-instance: ## Restart specific instance (use BASE_PORT=9000)
	@if [ -z "$(BASE_PORT)" ]; then \
		echo "❌ Error: BASE_PORT parameter required"; \
		echo "Usage: make dev-native-restart-instance BASE_PORT=9000"; \
		exit 1; \
	fi
	@echo "🔄 Restarting instance with BASE_PORT=$(BASE_PORT)..."
	@BASE_PORT=$(BASE_PORT) ./scripts/dev/restart-native.sh

# ═══════════════════════════════════════════════════════════
# CI/CD
# ═══════════════════════════════════════════════════════════

ci-build: clean install build ## Full CI build (clean + install + build)
	@echo "✓ CI build complete"

ci-test: test-coverage lint ## Run all CI tests with coverage and linting
	@echo "✓ CI tests complete"

# ═══════════════════════════════════════════════════════════
# DEPENDENCY MANAGEMENT
# ═══════════════════════════════════════════════════════════

check-outdated: ## Check for outdated dependencies
	@echo "🔍 Checking outdated dependencies..."
	@echo ""
	@echo "→ Checking Java/Gradle dependencies..."
	@echo "  Note: Gradle doesn't have built-in outdated check"
	@echo "  Check manually: https://spring.io/projects/spring-boot#learn"
	@echo ""
	@echo "→ Checking infrastructure npm packages..."
	@cd infrastructure && npm outdated || true
	@echo ""
	@echo "→ Checking web-frontend npm packages..."
	@cd web-frontend && npm outdated || true
	@echo ""
	@echo "✓ Outdated check complete"

update-deps: ## Update safe dependencies (patch/minor only)
	@echo "📦 Updating safe dependencies..."
	@echo ""
	@echo "→ Updating infrastructure npm packages..."
	@cd infrastructure && npm update
	@echo ""
	@echo "→ Updating web-frontend npm packages (safe updates)..."
	@cd web-frontend && npm update
	@echo ""
	@echo "✓ Dependencies updated"
	@echo ""
	@echo "⚠️  Note: Major version updates require manual updates and testing"
	@echo "   Run 'make check-outdated' to see available major updates"

audit-security: ## Run security audits and vulnerability scans on all projects
	@echo "🔒 Running security audits..."
	@echo ""
	@echo "→ Running Trivy security scans..."
	@./scripts/security-scan.sh
	@echo ""
	@echo "→ Auditing infrastructure..."
	@cd infrastructure && npm audit || true
	@echo ""
	@echo "→ Auditing web-frontend..."
	@cd web-frontend && npm audit || true
	@echo ""
	@echo "✓ Security audit complete"
	@echo ""
	@echo "📊 Security Reports:"
	@echo "  Trivy SARIF: security-reports/*.sarif"

# ═══════════════════════════════════════════════════════════
# UTILITIES
# ═══════════════════════════════════════════════════════════

check-generated-types: ## Verify generated API types are up to date
	@echo "🔍 Checking generated API types are up to date..."
	@cd web-frontend && npm run check:api-types
	@echo "✓ Generated types are up to date"

verify: lint check-generated-types test ## Pre-commit verification (lint + check + test)
	@echo "✓ Verification complete - ready to commit!"

all: clean install build test ## Complete workflow: clean, install, build, test
	@echo "✓ All tasks complete!"

watch-generate-types: ## Generate Swift types from OpenAPI spec for watchOS app
	@echo "🔄 Generating Swift types from OpenAPI spec..."
	@cd apps/BATbern-watch && ./scripts/generate-types.sh
	@echo "✓ Swift types generated"
	@echo ""
	@echo "💡 Generated files in: apps/BATbern-watch/BATbern-watch Watch App/Generated/"
