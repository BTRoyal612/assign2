# Variables
JAVA = java
JAVAC = javac
CP = -cp lib/json-20230227.jar:src/
SOURCES = src/main/client/GETClient.java \
					src/main/aggregation/AggregationServer.java \
          src/main/common/JSONHandler.java \
          src/main/common/LamportClock.java \
          src/main/content/ContentServer.java \
          src/main/network/NetworkHandler.java \
          src/main/network/SocketNetworkHandler.java \
          # src/test/client/GETClientTest.java \
          # src/test/common/JSONHandlerTest.java \
          # src/test/content/ContentServerTest.java \
          # src/test/network/StubNetworkHandler.java \

MAIN_CLASS = test.client.GETClientTest
AGGREGATION_SERVER = main.aggregation.AggregationServer
CONTENT_SERVER = main.content.ContentServer
GETCLIENT = main.client.GETClient

# Targets and their actions

all:
	$(JAVAC) $(CP) $(SOURCES)

clean:
	find . -name "*.class" -exec rm {} +

aggregation:
	$(JAVA) $(CP) $(AGGREGATION_SERVER)

content:
	$(JAVA) $(CP) $(CONTENT_SERVER) localhost 4567 src/main/client/input.txt

client:
	$(JAVA) $(CP) $(GETCLIENT) localhost:4567 IDS60901

run: all
	$(JAVA) $(CP) $(MAIN_CLASS)

.PHONY: all clean