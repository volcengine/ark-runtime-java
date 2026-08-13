.PHONY: lint compile setup-hooks

lint:
	mvn checkstyle:check -q

compile:
	mvn compile -q

setup-hooks:
	@mkdir -p .githooks
	@cp scripts/pre-commit .githooks/pre-commit
	@chmod +x .githooks/pre-commit
	@git config core.hooksPath .githooks
	@echo "Git hooks installed to .githooks/"
