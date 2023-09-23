# Variables
JAVA = java
JAVAC = javac
LIB = lib
SRC = src
CP = -cp $(LIB)/json-20230227.jar:$(SRC)/
CPTEST = -cp $(LIB)/*:$(SRC)/

MAIN_CLASSES = $(patsubst %.java,%.class,$(wildcard $(SRC)/main/**/*.java))
TEST_CLASSES = $(patsubst %.java,%.class,$(wildcard $(SRC)/test/**/*.java))

TEST_MAIN_CLASS = org.junit.platform.console.ConsoleLauncher
MAIN_CLASS = test.client.GETClientTest
AGGREGATION_SERVER = main.aggregation.AggregationServer
CONTENT_SERVER = main.content.ContentServer
GETCLIENT = main.client.GETClient

# Targets and their actions

all: $(MAIN_CLASSES)

$(SRC)/main/%.class: $(SRC)/main/%.java
	@$(JAVAC) $(CP) $<

$(SRC)/test/%.class: $(SRC)/test/%.java
	@$(JAVAC) $(CPTEST) $<

# Compile JUnit test files
test: $(TEST_CLASSES)

test_run: test
	@$(JAVA) $(CPTEST) $(TEST_MAIN_CLASS) --scan-class-path

clean:
	@find . -name "*.class" -exec rm {} +

aggregation: all
	@$(JAVA) $(CP) $(AGGREGATION_SERVER)

content: all
	@$(JAVA) $(CP) $(CONTENT_SERVER) localhost 4567 $(SRC)/main/content/input.txt

client: all
	@$(JAVA) $(CP) $(GETCLIENT) localhost:4567 IDS60901

run: all
	@$(JAVA) $(CP) $(MAIN_CLASS)

.PHONY: all clean test
