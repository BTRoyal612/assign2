# Variables
JAVA = java
JAVAC = javac
LIB = lib
SRC = src
CP = -cp $(LIB)/gson-2.10.1.jar:$(SRC)/
CPTEST = -cp $(LIB)/*:$(SRC)/

MAIN_CLASSES = $(patsubst %.java,%.class,$(wildcard $(SRC)/main/**/*.java))
TEST_CLASSES = $(patsubst %.java,%.class,$(wildcard $(SRC)/test/**/*.java))

TEST_MAIN_CLASS = org.junit.platform.console.ConsoleLauncher
LOAD_BALANCER = main.aggregation.LoadBalancer
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
	@$(JAVA) $(CPTEST) $(TEST_MAIN_CLASS) --details=verbose --scan-class-path

clean:
	@find . -name "*.class" -exec rm {} +

aggregation: all
	@$(JAVA) $(CP) $(AGGREGATION_SERVER)

loadbalancer: all
	@$(JAVA) $(CP) $(LOAD_BALANCER)

loadbalancer1: all
	@$(JAVA) $(CP) $(LOAD_BALANCER) 4567 1

content1: all
	@$(JAVA) $(CP) $(CONTENT_SERVER) localhost 4567 $(SRC)/main/content/input_v1.txt

content2: all
	@$(JAVA) $(CP) $(CONTENT_SERVER) localhost 4567 $(SRC)/main/content/input_v2.txt

content3: all
	@$(JAVA) $(CP) $(CONTENT_SERVER) localhost 4567 $(SRC)/main/content/input_v3.txt

client1: all
	@$(JAVA) $(CP) $(GETCLIENT) localhost:4567 IDS60901

client2: all
	@$(JAVA) $(CP) $(GETCLIENT) http://localhost.domain.domain:4567 IDS90210

client3: all
	@$(JAVA) $(CP) $(GETCLIENT) http://localhost:4567 IDS60901

.PHONY: all clean test
