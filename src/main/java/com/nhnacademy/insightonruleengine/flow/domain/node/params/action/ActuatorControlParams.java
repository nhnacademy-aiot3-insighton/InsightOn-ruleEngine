package com.nhnacademy.insightonruleengine.flow.domain.node.params.action;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Set;

/**
 * node_type = ACTUATOR_CONTROL.
 * 실제 장치 제어는 Core가 담당하며, Engine은 이 설정을 Core 명령 DTO로 변환해 발행한다.
 * callerService는 사용자가 설정하지 않고 Engine이 outbound DTO에 주입한다.
 */
public record ActuatorControlParams(
        @NotBlank @Size(max = 100) String actuatorType,
        @NotBlank @Size(max = 100) String command,
        @NotBlank @Size(max = 500) String commandValue
) implements NodeParams {

    private static final String COMMAND_POWER = "power";

    @JsonIgnore
    @AssertTrue(message = "지원하지 않는 액추에이터 명령 또는 값입니다.")
    public boolean isSupportedCommand() {
        if (isBlank(actuatorType) || isBlank(command) || isBlank(commandValue)) {
            return true;
        }
        return switch (actuatorType) {
            case "AIRCON" -> switch (command.toLowerCase(Locale.ROOT)) {
                case COMMAND_POWER -> allowed("ON", "OFF");
                case "mode" -> allowed("COOL", "DRY", "FAN", "AUTO");
                case "temperature" -> numericRange(18, 30);
                default -> false;
            };
            case "AIR_PURIFIER" -> switch (command.toLowerCase(Locale.ROOT)) {
                case COMMAND_POWER -> allowed("ON", "OFF");
                case "mode" -> allowed("AUTO", "SLEEP", "TURBO");
                default -> false;
            };
            case "VENTILATION_FAN" -> switch (command.toLowerCase(Locale.ROOT)) {
                case COMMAND_POWER -> allowed("ON", "OFF");
                case "mode" -> allowed("LOW", "MID", "HIGH");
                default -> false;
            };
            default -> false;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean allowed(String... values) {
        return Set.of(values).contains(commandValue.toUpperCase(Locale.ROOT));
    }

    private boolean numericRange(double min, double max) {
        try {
            double value = Double.parseDouble(commandValue);
            return value >= min && value <= max;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
