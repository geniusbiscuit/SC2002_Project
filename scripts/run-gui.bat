@echo off
rem Windows: compile the game and the optional window, then start it (needs JDK 17 or newer on the PATH).
rem   scripts\run-gui.bat                 Stage 3   |   scripts\run-gui.bat --classic   Stage 2   |   --regular-only   Stage 1
cd /d "%~dp0\.."
if exist out rmdir /s /q out
mkdir out
javac --release 17 -d out src\main\java\restaurantrush\boundary\*.java src\main\java\restaurantrush\control\*.java src\main\java\restaurantrush\entity\*.java src\main\java\restaurantrush\util\*.java extras\gui\src\restaurantrush\boundary\gui\*.java
if errorlevel 1 exit /b 1
java -cp out restaurantrush.boundary.gui.GuiMain %*
