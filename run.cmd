@echo off
rem LeetVise - revise the LeetCode problems you've already solved.
rem
rem Copyright (c) 2026 Vishwajeet Pratap Singh
rem
rem Author:    Vishwajeet Pratap Singh
rem GitHub:    https://github.com/vishwajeet-singhh
rem LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
rem Portfolio: https://vishwajeet.me
rem Source:    https://github.com/vishwajeet-singhh/LeetVise

rem Start LeetVise on Windows: double-click this file, or run "run.cmd" in a terminal.
rem Needs Java 21 or newer. Maven is downloaded automatically by mvnw.cmd.
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 if not defined JAVA_HOME (
  echo LeetVise needs Java 21 or newer. Install it ^(e.g. https://adoptium.net^) and run this again.
  pause
  exit /b 1
)

if not exist .env (
  copy .env.example .env >nul
  echo Created .env - add your LeetCode cookie there to load everything ^(see README^).
)

echo Starting LeetVise... ^(the first run downloads dependencies, give it a minute^)
call mvnw.cmd -q spring-boot:run
if errorlevel 1 pause

rem LeetVise | (c) 2026 Vishwajeet Pratap Singh | github.com/vishwajeet-singhh | linkedin.com/in/vishwajeetsage | vishwajeet.me
