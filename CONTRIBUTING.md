# Contributing to V4AW

First off, thank you for considering contributing to V4AW! It's people like you that make V4AW such a great tool.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)

## 📜 Code of Conduct

This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## 🤝 How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, please include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected behavior** vs **actual behavior**
- **Screenshots** if applicable
- **Device information** (Android version, device model)
- **App version** you're using

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- **Clear title and description**
- **Use case** - why is this enhancement useful?
- **Possible implementation** - if you have ideas how to implement it

### Pull Requests

- Fill in the required template
- Do not include issue numbers in the PR title
- Follow the [Coding Standards](#coding-standards)
- Follow the [Commit Guidelines](#commit-guidelines)

## 🛠️ Development Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or later
- Android SDK (minSdk 30, targetSdk 36)

### Setup Steps

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR_USERNAME/V4AW.git
   cd V4AW
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Choose the V4AW directory

3. **Create a Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Make Your Changes**
   - Write clean, maintainable code
   - Add appropriate comments
   - Test your changes thoroughly

5. **Commit and Push**
   ```bash
   git add .
   git commit -m "feat: add your feature"
   git push origin feature/your-feature-name
   ```

6. **Create Pull Request**
   - Go to your fork on GitHub
   - Click "New Pull Request"
   - Fill in the PR template

## 📏 Coding Standards

### Kotlin Style Guide

- Follow [Kotlin official coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Use appropriate visibility modifiers

### Architecture

- Follow Clean Architecture principles
- Maintain separation of concerns
- Use MVVM pattern for UI
- Keep business logic in use cases

### Documentation

- Add KDoc comments for public APIs
- Update README.md if needed
- Keep code comments in English
- Document complex algorithms

### Testing

- Write unit tests for business logic
- Write UI tests for critical user flows
- Ensure all tests pass before submitting PR

## 📝 Commit Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/) specification:

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `chore`: Changes to the build process or auxiliary tools

### Examples

```bash
feat(download): add pause/resume support for downloads
fix(parser): fix video source extraction from iframe
docs(readme): update installation instructions
refactor(player): optimize ExoPlayer initialization
```

## 🔄 Pull Request Process

1. **Update Documentation**
   - Update README.md if you change functionality
   - Update API documentation if you add/modify public APIs

2. **Update CHANGELOG.md**
   - Add your changes to the Unreleased section
   - Follow the existing format

3. **Ensure Tests Pass**
   - Run all unit tests: `./gradlew test`
   - Run all instrumentation tests: `./gradlew connectedAndroidTest`

4. **Code Review**
   - Address all review comments
   - Keep the PR up to date with main branch

5. **Merge Requirements**
   - At least 1 approval from a maintainer
   - All CI checks must pass
   - No merge conflicts

## 🏷️ Branch Naming Convention

- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates
- `refactor/description` - Code refactoring
- `test/description` - Test additions/modifications
- `chore/description` - Maintenance tasks

## ❓ Questions?

Feel free to open an issue with the label `question` or reach out to the maintainers.

---

Thank you for contributing! 🎉
