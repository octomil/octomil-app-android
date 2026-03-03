# Contributing to Octomil Android App

Thank you for your interest in contributing to the Octomil Android App! This guide will help you get started.

## Table of Contents

- [Reporting Bugs](#reporting-bugs)
- [Requesting Features](#requesting-features)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)
- [Testing Requirements](#testing-requirements)
- [Commit Message Conventions](#commit-message-conventions)
- [Code of Conduct](#code-of-conduct)

## Reporting Bugs

If you find a bug, please open an issue on [GitHub Issues](https://github.com/octomil/octomil-app-android/issues) with the following information:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs. actual behavior
- Android version, device model, and API level
- Relevant logs or error messages
- A minimal reproduction, if possible

## Requesting Features

Feature requests are welcome! Please open an issue on [GitHub Issues](https://github.com/octomil/octomil-app-android/issues) and include:

- A clear description of the feature and the problem it solves
- Example usage or UI mockups, if applicable
- Any relevant context or alternatives you have considered

## Development Setup

This app uses the [Octomil Android SDK](https://github.com/octomil/octomil-android) as a git submodule via Gradle composite build.

1. Fork the repository and clone your fork **with submodules**:
   ```bash
   git clone --recurse-submodules https://github.com/<your-fork>/octomil-app-android.git
   cd octomil-app-android
   ```

2. If you already cloned without `--recurse-submodules`, initialize the submodule:
   ```bash
   git submodule update --init --recursive
   ```

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

4. Verify the setup by running the test suite:
   ```bash
   ./gradlew test
   ```

## Pull Request Process

1. **Fork** the repository and create a new branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Make your changes** in focused, incremental commits.
3. **Ensure all tests pass** and add new tests for any new functionality.
4. **Push** your branch to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
5. **Open a Pull Request** against the `main` branch of this repository.
6. **Respond to review feedback** promptly. A maintainer will review your PR and may request changes.
7. Once approved, a maintainer will merge your PR.

### PR Guidelines

- Keep PRs focused on a single change or feature.
- Include a clear description of what the PR does and why.
- Reference any related issues (e.g., "Closes #42").
- Ensure CI checks pass before requesting review.

## Code Style

This project enforces consistent code style using the following tools:

- **[ktlint](https://pinterest.github.io/ktlint/)** -- Linting and formatting for Kotlin code. Run locally with:
  ```bash
  ./gradlew ktlintCheck
  ./gradlew ktlintFormat
  ```

Please ensure your code passes ktlint checks before submitting a PR.

## Testing Requirements

- **All tests must pass.** Run the full test suite with:
  ```bash
  ./gradlew test
  ```
- **New code must include tests.** All new features and bug fixes should be accompanied by appropriate JUnit test coverage.
- **Coverage is required.** Aim to maintain or improve the current code coverage level. Avoid submitting PRs that reduce coverage.

## Commit Message Conventions

Use clear, descriptive commit messages following this format:

```
<type>: <short summary>

<optional body with more detail>
```

**Types:**
- `feat` -- A new feature
- `fix` -- A bug fix
- `docs` -- Documentation changes
- `test` -- Adding or updating tests
- `refactor` -- Code refactoring with no functional change
- `chore` -- Maintenance tasks (CI, dependencies, etc.)

**Examples:**
```
feat: add model download progress indicator
fix: resolve crash on pairing screen rotation
docs: update setup instructions in README
```

## Code of Conduct

We are committed to providing a welcoming and inclusive experience for everyone. All participants are expected to:

- **Be respectful** -- Treat others with courtesy and respect. Disagreements are fine; personal attacks are not.
- **Be constructive** -- Provide helpful feedback and focus on improving the project.
- **Be inclusive** -- Welcome newcomers and help them get started.
- **Be professional** -- Harassment, discrimination, and abusive behavior will not be tolerated.

If you experience or witness unacceptable behavior, please report it by opening an issue or contacting the maintainers directly.

---

Thank you for contributing to Octomil!
