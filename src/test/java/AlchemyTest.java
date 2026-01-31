import com.codeborne.selenide.WebDriverRunner;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.appium.SelenideAppium.$;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AlchemyTest {
    
    private static AndroidDriver driver;

    @BeforeAll
    static void setup() throws Exception {
        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName("Android")
                .setDeviceName("emulator-5554")
                .setAutomationName("UiAutomator2")
                .setAppPackage("com.ilyin.alchemy")
                .setNoReset(true);

        driver = new AndroidDriver(
                new URL("http://127.0.0.1:4723"),
                options
        );

        WebDriverRunner.setWebDriver(driver);
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Получение подсказок за просмотр рекламы")
    void testGetHintFromAd() throws InterruptedException {

        Allure.step("Открываем приложение и заходим в игру", () -> {
            driver.activateApp("com.ilyin.alchemy");
            $(AppiumBy.xpath("//y2.f1/android.view.View/android.view.View/android.view.View/android.view.View[5]/android.widget.Button"))
                .shouldBe(visible, Duration.ofSeconds(20)).click();
        });

        final int[] countBefore = new int[1];

        Allure.step("Запоминаем количество подсказок ДО", () -> {
            String textBefore = $(AppiumBy.xpath("(//android.widget.TextView[@text])[1]"))
                    .shouldBe(visible, Duration.ofSeconds(10)).getText();
            countBefore[0] = extractNumber(textBefore);
            System.out.println("Подсказок до теста: " + countBefore[0]);
        });

        Allure.step("Запускаем просмотр рекламы", () -> {
            Thread.sleep(3000);
            $(AppiumBy.xpath("//y2.f1/android.view.View/android.view.View/android.view.View/android.view.View[1]/android.view.View[1]/android.view.View[2]")).shouldBe(visible).click();
            Thread.sleep(10_000);
            $(AppiumBy.xpath("//android.widget.TextView[@text='Watch']")).shouldBe(visible).click();

        });

        Allure.step("Цикл закрытия рекламы", () -> {
            long start = System.currentTimeMillis();
            long timeout = 120_000; 
            Thread.sleep(5000); // Даем рекламе стартануть

            while (!$(AppiumBy.xpath("//android.widget.TextView[@text='Your hints']")).exists() 
                   && System.currentTimeMillis() - start < timeout) {

                if (!driver.getCurrentPackage().equals("com.ilyin.alchemy")) {
                    System.out.println("Вылетели в Play Market. Возвращаемся...");
                    driver.navigate().back();
                    Thread.sleep(3000);
                    continue;
                }

                WebElement bestBtn = findBestCloseButton(driver);

                if (bestBtn != null) {
                    try {
                        System.out.println("Нажал на найденную кнопку закрытия");
                        bestBtn.click();
                        Thread.sleep(4000);
                    } catch (Exception ignored) {}
                } else {
                    long timePassed = System.currentTimeMillis() - start;
                    if (timePassed < 25_000) {
                        System.out.println("Реклама идет, жду появления кнопки...");
                        Thread.sleep(3000);
                    } else {
                        System.out.println("Кнопок нет долго. Тапаю в верхний угол.");
                        int x = (int) (driver.manage().window().getSize().getWidth() * 0.94);
                        int y = (int) (driver.manage().window().getSize().getHeight() * 0.06);
                        tapByCoordinates(driver, x, y);
                        Thread.sleep(4000);
                    }
                }
            }
        });

        Allure.step("Проверяем начисление подсказок", () -> {
            Thread.sleep(1000); 

            String textAfter = "";
            List<WebElement> textViews = driver.findElements(AppiumBy.className("android.widget.TextView"));
            
            for (WebElement tv : textViews) {
                String val = tv.getText().trim();
                if (val.matches("^\\d+$")) {
                    textAfter = val;
                    break;
                }
            }

            if (textAfter.isEmpty()) {
                throw new RuntimeException("Не удалось найти числовое значение подсказок на экране!");
            }

            int countAfter = Integer.parseInt(textAfter);
            System.out.println("Результат: было " + countBefore[0] + ", стало " + countAfter);
            
            Assertions.assertTrue(countAfter > countBefore[0], 
                "Баланс не увеличился! До: " + countBefore[0] + ", После: " + countAfter);
        });
    }

    private int extractNumber(String text) {
        String digits = text.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            try {
                String fallback = $(AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"^\\\\d+$\")"))
                            .shouldBe(visible, Duration.ofSeconds(5)).getText();
                return Integer.parseInt(fallback);
            } catch (Exception e) {
                    return 0;
            }
        }
        return Integer.parseInt(digits);
    }

    private static WebElement findBestCloseButton(AndroidDriver driver) {
        List<WebElement> candidates = driver.findElements(AppiumBy.xpath("//*[@clickable='true']"));
        int screenWidth = driver.manage().window().getSize().getWidth();
        int screenHeight = driver.manage().window().getSize().getHeight();
        WebElement best = null;
        int bestScore = 0;

        for (WebElement el : candidates) {
            try {
                int s = score(el, screenWidth, screenHeight);
                if (s > bestScore && s > 0) { 
                    bestScore = s;
                    best = el;
                }
            } catch (Exception e) {}
        }
        return best;
    }

    private static int score(WebElement el, int screenWidth, int screenHeight) {
        int score = 0;
        Rectangle r = el.getRect();
        if (r.getY() < screenHeight * 0.20) score += 3; 
        if (r.getX() > screenWidth * 0.70) score += 3;  
        String fullInfo = (el.getText() + " " + el.getAttribute("content-desc") + " " + el.getAttribute("resource-id")).toLowerCase();
        if (fullInfo.matches(".*(close|skip|dismiss|x|×|✕|закрыть|пропустить).*")) score += 15;
        if (fullInfo.matches(".*(next|continue|forward|далее|arrow|reward).*")) score += 10;
        if (fullInfo.matches(".*(install|open|more|shop|download|установить|скачать).*")) score -= 50;
        return score;
    }

    private static void tapByCoordinates(AndroidDriver driver, int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(tap));
    }
}