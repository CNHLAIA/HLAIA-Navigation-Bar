# PRD: Create `.trellis/spec/frontend/` with real frontend coding conventions

## Summary

Document the actual coding conventions used in this Vue 3 frontend project by creating comprehensive spec files under `.trellis/spec/frontend/`.

## Background

The project already has backend specs at `.trellis/spec/backend/`, but no frontend specs exist. New features and AI sub-agents need documented conventions to match the project's style consistently.

## Requirements

### Files to Create

- [x] `.trellis/spec/frontend/directory-structure.md` -- Where files go, component organization, naming conventions with real directory tree
- [x] `.trellis/spec/frontend/component-guidelines.md` -- Vue 3 composition API patterns, component structure, props/emits, dialog reuse, drag-and-drop, recursive components
- [x] `.trellis/spec/frontend/state-management.md` -- Pinia Setup Store patterns (defineStore, state/getters/actions), store-API interaction, cross-store coordination
- [x] `.trellis/spec/frontend/api-patterns.md` -- Axios wrapper setup, API module organization, JWT interceptors, token refresh, error handling
- [x] `.trellis/spec/frontend/quality-guidelines.md` -- CSS variables/scoping, i18n patterns, icon conventions, responsive design, forbidden patterns, code review checklist
- [x] `.trellis/spec/frontend/index.md` -- Index page with links to all guides and key conventions summary

### Content Rules

- [x] Written in Chinese
- [x] Include real file paths from the codebase
- [x] Include real code snippets from actual source files
- [x] Document what the code ACTUALLY does, not ideals
- [x] Each file thorough enough for an AI sub-agent to match the project's style

### Finalization

- [x] `index.md` status marked "Done" for all guides
- [x] PRD checklist items marked done

## Acceptance Criteria

1. All six spec files exist under `.trellis/spec/frontend/`
2. Each file references real code from `frontend/src/`
3. Content is accurate to the actual codebase patterns
4. index.md links to all guides and has a key conventions summary
