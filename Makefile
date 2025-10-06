# BATbern Platform - Unified Build System
# =========================================
# Makefile for building and testing all components of the polyglot monorepo
#
# Project Structure:
#   - Java/Gradle: shared-kernel, api-gateway, 5x domain services
#   - TypeScript/Node: infrastructure (CDK), web-frontend (React)
#
# Usage: make <target>
# Run 'make help' to see all available targets

.DEFAULT_GOAL := help
.PHONY: help install build test lint clean docker-up docker-down docker-build verify ci-build ci-test check-outdated update-deps audit-security all

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
	@echo ""
	@echo "🧪 Testing:"
	@echo "  make test             - Run all tests"
	@echo "  make test-java        - Run Java tests only"
	@echo "  make test-node        - Run Node.js tests only"
	@echo "  make test-coverage    - Run tests with coverage reports"
	@echo ""
	@echo "✨ Code Quality:"
	@echo "  make lint             - Run all linters"
	@echo "  make lint-java        - Run Java linters (checkstyle)"
	@echo "  make lint-node        - Run Node.js linters (eslint)"
	@echo "  make format           - Format all code"
	@echo "  make format-check     - Check code formatting"
	@echo ""
	@echo "🧹 Cleanup:"
	@echo "  make clean            - Clean all build artifacts"
	@echo "  make clean-java       - Clean Java build artifacts"
	@echo "  make clean-node       - Clean Node.js build artifacts"
	@echo ""
	@echo "🐳 Docker:"
	@echo "  make docker-up        - Start all services with Docker Compose"
	@echo "  make docker-down      - Stop all Docker services"
	@echo "  make docker-build     - Build Docker images for all services"
	@echo ""
	@echo "🚀 CI/CD:"
	@echo "  make ci-build         - Full CI build (clean + build + test)"
	@echo "  make ci-test          - Run all CI tests"
	@echo ""
	@echo "📊 Dependency Management:"
	@echo "  make check-outdated   - Check for outdated dependencies"
	@echo "  make update-deps      - Update safe dependencies (patch/minor)"
	@echo "  make audit-security   - Run security audits"
	@echo ""
	@echo "🔍 Utilities:"
	@echo "  make verify           - Pre-commit verification (lint + test)"
	@echo "  make all              - Clean + Install + Build + Test"
	@echo ""

# ═══════════════════════════════════════════════════════════
# INSTALLATION
# ═══════════════════════════════════════════════════════════

install: install-java install-node ## Install all dependencies

install-java: ## Install Java/Gradle dependencies
	@echo "📦 Installing Java dependencies..."
	@echo "→ Building shared-kernel..."
	@cd shared-kernel && ./gradlew build publishToMavenLocal -x test
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
	@./gradlew build -x test --parallel
	@echo "✓ Java build complete"

build-node: ## Build all Node.js projects
	@echo "🔨 Building Node.js projects..."
	@echo "→ Building infrastructure..."
	@cd infrastructure && npm run build
	@echo "→ Building web-frontend..."
	@cd web-frontend && npm run build
	@echo "✓ Node.js build complete"

# ═══════════════════════════════════════════════════════════
# TESTING
# ═══════════════════════════════════════════════════════════

test: test-java test-node ## Run all tests

test-java: ## Run Java tests
	@echo "🧪 Running Java tests..."
	@./gradlew test --parallel
	@echo "✓ Java tests complete"

test-node: ## Run Node.js tests
	@echo "🧪 Running Node.js tests..."
	@echo "→ Testing infrastructure..."
	@cd infrastructure && npm test
	@echo "→ Testing web-frontend..."
	@cd web-frontend && npm run test:run
	@echo "✓ Node.js tests complete"

test-coverage: ## Run tests with coverage reports
	@echo "🧪 Running tests with coverage..."
	@echo "→ Java coverage (JaCoCo)..."
	@./gradlew test jacocoTestReport --parallel
	@echo "→ Node.js coverage..."
	@cd infrastructure && npm test -- --coverage || true
	@cd web-frontend && npm run test:coverage
	@echo "✓ Coverage reports generated"
	@echo ""
	@echo "📊 Coverage Reports:"
	@echo "  Java:     build/reports/jacoco/test/html/index.html"
	@echo "  CDK:      infrastructure/coverage/index.html"
	@echo "  Frontend: web-frontend/coverage/index.html"

# ═══════════════════════════════════════════════════════════
# CODE QUALITY
# ═══════════════════════════════════════════════════════════

lint: lint-java lint-node ## Run all linters

lint-java: ## Run Java linters
	@echo "✨ Running Java linters..."
	@./gradlew check -x test
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

clean: clean-java clean-node ## Clean all build artifacts

clean-java: ## Clean Java build artifacts
	@echo "🧹 Cleaning Java build artifacts..."
	@./gradlew clean
	@echo "✓ Java clean complete"

clean-node: ## Clean Node.js build artifacts
	@echo "🧹 Cleaning Node.js build artifacts..."
	@rm -rf infrastructure/node_modules infrastructure/cdk.out
	@rm -rf web-frontend/node_modules web-frontend/dist web-frontend/coverage
	@echo "✓ Node.js clean complete"

# ═══════════════════════════════════════════════════════════
# DOCKER
# ═══════════════════════════════════════════════════════════

docker-up: ## Start all services with Docker Compose
	@echo "🐳 Starting Docker services..."
	@docker-compose up -d
	@echo "✓ Docker services started"
	@echo ""
	@echo "Services running at:"
	@echo "  API Gateway:   http://localhost:8080"
	@echo "  Web Frontend:  http://localhost:3000"

docker-down: ## Stop Docker services
	@echo "🐳 Stopping Docker services..."
	@docker-compose down
	@echo "✓ Docker services stopped"

docker-build: ## Build Docker images for all services
	@echo "🐳 Building Docker images..."
	@./scripts/build-and-push-services.sh local
	@echo "✓ Docker images built"

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

audit-security: ## Run security audits on all projects
	@echo "🔒 Running security audits..."
	@echo ""
	@echo "→ Auditing infrastructure..."
	@cd infrastructure && npm audit
	@echo ""
	@echo "→ Auditing web-frontend..."
	@cd web-frontend && npm audit
	@echo ""
	@echo "✓ Security audit complete"

# ═══════════════════════════════════════════════════════════
# UTILITIES
# ═══════════════════════════════════════════════════════════

verify: lint test ## Pre-commit verification (lint + test)
	@echo "✓ Verification complete - ready to commit!"

all: clean install build test ## Complete workflow: clean, install, build, test
	@echo "✓ All tasks complete!"
