package de.igslandstuhl.database.plugins.config;

public class IntSetting extends PluginSetting<Integer> {
    private int minValue = Integer.MIN_VALUE;
    private int maxValue = Integer.MAX_VALUE;
    public IntSetting(String key, String name, String description, int defaultValue) {
        super(key, name, description, defaultValue);
    }

    @Override
    public String toJSON() {
        return "{" +
                "\"key\":\"" + getKey() + "\"," +
                "\"name\":\"" + getName() + "\"," +
                "\"description\":\"" + getDescription() + "\"," +
                "\"defaultValue\":" + getDefaultValue() + "," +
                "\"value\":" + getValue() + "," +
                "\"minValue\":" + minValue + "," +
                "\"maxValue\":" + maxValue +
                "}";
    }
    public int getMinValue() {
        return minValue;
    }
    public void setMinValue(int minValue) {
        if (minValue > maxValue) throw new IllegalArgumentException("minValue cannot be greater than maxValue");
        this.minValue = minValue;
    }
    public int getMaxValue() {
        return maxValue;
    }
    public void setMaxValue(int maxValue) {
        if (maxValue < minValue) throw new IllegalArgumentException("maxValue cannot be less than minValue");
        this.maxValue = maxValue;
    }
    public void setBounds(int minValue, int maxValue) {
        if (minValue > maxValue) throw new IllegalArgumentException("minValue cannot be greater than maxValue");
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
}
