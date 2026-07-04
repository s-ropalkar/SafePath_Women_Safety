@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is set
@REM set MAVEN_BATCH_ECHO to 'on' to enable echo

@ECHO OFF
setlocal EnableDelayedExpansion

set ERROR_CODE=0

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto chkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:chkJCmd
if not exist "%JAVACMD%" (
  echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. >&2
  goto error
)

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

@REM Stop running server before clean (unlocks target\safepath-1.0.0.jar)
echo %* | findstr /i /c:"clean" >nul
if not errorlevel 1 (
  if exist "%MAVEN_PROJECTBASEDIR%\stop-server.bat" call "%MAVEN_PROJECTBASEDIR%\stop-server.bat" >nul 2>&1
)

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

set "JVM_CONFIG_MAVEN_PROPS="
if exist "%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config" (
  for /f "usebackq delims=" %%a in ("%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config") do (
    set "JVM_CONFIG_MAVEN_PROPS=!JVM_CONFIG_MAVEN_PROPS! %%a"
  )
)

"%JAVACMD%" %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% !JVM_CONFIG_MAVEN_PROPS! -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
endlocal & set ERROR_CODE=%ERROR_CODE%
exit /b %ERROR_CODE%
