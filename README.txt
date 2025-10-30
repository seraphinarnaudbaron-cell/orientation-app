Orientation App - packaged project
Location center: Pont de l'Amour, Villard-de-Lans (lat: 45.06194, lon: 5.56745)
Admin password set to: Lacacahuette38
Theme: green / white / black

Files:
- main.py : main application
- beacons.json : sample beacons (12) within ~2km x 2km area around Pont de l'Amour
- assets/ : placeholder punch images (punch_001.png ... punch_012.png)
- buildozer.spec : minimal spec file

Build on Linux/WSL:
1. Install buildozer, dependencies (see Kivy + Buildozer docs)
2. cd to project folder and run: buildozer -v android debug
