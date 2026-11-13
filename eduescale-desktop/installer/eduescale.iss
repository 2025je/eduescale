#ifndef AppVersion
  #error AppVersion must be supplied by the build command.
#endif

#define AppName "EduEscale"
#define AppPublisher "EduEscale"
#define AppURL "https://github.com/2025je/eduescale"
#define ProjectDir AddBackslash(SourcePath) + ".."
#define DistDir AddBackslash(ProjectDir) + "dist"
#define AppExeName "eduescale-gui.exe"

[Setup]
AppId={{593080dd-bb91-4880-95c3-a52cae8c1519}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
DefaultDirName={autopf}\EduEscale
DefaultGroupName=EduEscale
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExeName}
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile={#ProjectDir}\eduescale\gui\assets\edutrack-logo.ico
OutputDir={#DistDir}\installer
OutputBaseFilename=EduEscale-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
VersionInfoVersion={#AppVersion}.0

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked

[Files]
Source: "{#DistDir}\eduescale-gui\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#DistDir}\eduescale-scraper\*"; DestDir: "{app}\eduescale-scraper"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\EduEscale"; Filename: "{app}\{#AppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\EduEscale"; Filename: "{app}\{#AppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Iniciar EduEscale"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent
