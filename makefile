# Variables
JDK_DIR = jdk/bin
JAVA = $(JDK_DIR)/java
JAVAC = $(JDK_DIR)/javac
LIB = lib
SRC = src
OUT = out
CP = -cp $(LIB)/gson-2.10.1.jar:$(OUT)/
CPTEST = -cp $(LIB)/*:$(OUT)/

MAIN_SOURCES = $(wildcard $(SRC)/main/**/*.java)
TEST_SOURCES = $(wildcard $(SRC)/test/**/*.java)

TEST_MAIN_CLASS = org.junit.platform.console.ConsoleLauncher
LOAD_BALANCER = main.aggregation.LoadBalancer
AGGREGATION_SERVER = main.aggregation.AggregationServer
CONTENT_SERVER = main.content.ContentServer
GETCLIENT = main.client.GETClient

# Targets and their actions
setup:
	@chmod +x $(JAVA) $(JAVAC)

all: compile-main

compile-main:
	@mkdir -p $(OUT)
	@$(JAVAC) $(CP) -d $(OUT) $(MAIN_SOURCES)

compile-test: compile-main
	@$(JAVAC) $(CPTEST) -d $(OUT) $(TEST_SOURCES)

test: compile-test
	@$(JAVA) $(CPTEST) $(TEST_MAIN_CLASS) --scan-classpath 2>/dev/null

clean:
	@find . -name "*.class" -exec rm {} +
	@rm -rf $(OUT)

run: all
	@$(JAVA) $(CP) $(MAIN_CLASS)

loadbalancer: all
	@$(JAVA) $(CP) $(LOAD_BALANCER)

loadbalancer1: all
	@$(JAVA) $(CP) $(LOAD_BALANCER) 4567 1

loadbalancer5: all
	@$(JAVA) $(CP) $(LOAD_BALANCER) 4567 5

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

.PHONY: all clean test run compile-main compile-test aggregation loadbalancer loadbalancer1 loadbalancer5 content1 content2 content3 client1 client2 client3
