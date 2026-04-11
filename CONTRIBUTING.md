# Contributing to Laconical Player

First off, thank you for considering contributing to Laconical Player! It's people like you who make the open-source community such an amazing place to learn, inspire, and create.

## Vibe-Coding with AI

This project is built using a "vibe-coding" approach. While I value deep technical knowledge, I lean heavily on **Claude Code** (and occasionally Google Antigravity) for architecting and implementing the complex systems. I focus on the vibe, the user experience, the aesthetics, and the overall feel of the app, and let the AI handle the heavy lifting of the Android SDK.

## How Can I Contribute?

### Reporting Bugs
If you find a bug, please search the [issues](https://github.com/btema2/laconical-player/issues) to see if it has already been reported. If not, open a new issue and provide as much detail as possible, including steps to reproduce.

### Suggesting Enhancements
I am always looking for ways to improve the vibe and functionality of Laconical Player. If you have an idea, please open an issue to discuss it.

### Pull Requests
I welcome pull requests! 
1. Fork the repository.
2. Create a new branch for your feature or bugfix.
3. Commit your changes with clear, descriptive messages.
4. Push to your fork and submit a pull request.

Please make sure your code:
- Builds successfully (`./gradlew assembleDebug`)
- Follows the `LaconicalTheme` design system — no hardcoded colors or raw hex values in component code
- Keeps animations on compositor-friendly properties (`scale`, `alpha`, `offset`) only
- Does not mutate state inside Compose `Canvas` draw blocks

## Community

Laconical Player is an open-source project and anyone is welcome to join our community. Whether you're a seasoned Android dev or just starting out with vibe-coding, I'd love to have your input!

---

By contributing, you agree that your contributions will be licensed under the project's [GPL-3.0 License](LICENSE).
