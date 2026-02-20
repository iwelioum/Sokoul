@echo off
ECHO Lancement de l'environnement de developpement de Sokoul...

REM Change directory to the root of the project where this script is located
cd /d "%~dp0"

ECHO Lancement du Backend (Rust)...
REM Opens a new window titled "Sokoul Backend", navigates to the backend folder, and runs cargo
START "Sokoul Backend" cmd /c "cd Sokoul && cargo run"

ECHO Lancement du Frontend (SvelteKit)...
REM Opens a new window titled "Sokoul Frontend", navigates to the dashboard folder, and runs the dev server
START "Sokoul Frontend" cmd /c "cd Sokoul\dashboard && npm run dev"

ECHO.
ECHO Deux nouvelles fenetres ont ete ouvertes pour le backend et le frontend.
ECHO Vous pouvez fermer cette fenetre.
