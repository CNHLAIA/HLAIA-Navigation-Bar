# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

This directory contains guidelines for backend development. Each file documents the actual coding conventions used in this Spring Boot 4 + MyBatis-Plus project.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Module organization and file layout | Done |
| [Database Guidelines](./database-guidelines.md) | ORM patterns, queries, migrations | Done |
| [Error Handling](./error-handling.md) | Error types, handling strategies | Done |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | Done |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | Done |

---

## Key Conventions Summary

- **Framework**: Spring Boot 4.0.5, Java 25, MyBatis-Plus 3.5.15
- **Package namespace**: `jakarta.*` (not `javax.*`)
- **DI style**: Constructor injection via `@RequiredArgsConstructor` (never `@Autowired`)
- **Response format**: All APIs return `Result<T>` with `{code, message, data}`
- **Error handling**: `BusinessException` + `GlobalExceptionHandler`, HTTP 200 + error code in body
- **Database queries**: `LambdaQueryWrapper` only (no XML mappers, no `@Select`)
- **Chinese learning comments**: Required on all Java files for beginner education
