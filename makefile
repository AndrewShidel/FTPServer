build:
	javac -d ./bin src/com/ftpServer/*.java
	cd bin && jar -cvfm FTPServer.jar MANIFEST.MF com/ftpServer/*.class && cd -
