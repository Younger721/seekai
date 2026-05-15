package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 图表生成工具 - 生成 ECharts 交互式图表配置
 * 支持折线图、柱状图、饼图、散点图、面积图等
 */
@Slf4j
@Component
public class ChartTools {

    /**
     * 生成折线图配置
     *
     * @param title      图表标题
     * @param xAxisData  X轴数据（用逗号分隔）
     * @param yAxisData  Y轴数据（用逗号分隔）
     * @param seriesName 数据系列名称
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成折线图。适用于显示趋势数据，如股票走势、温度变化、销售趋势等。返回 ECharts 配置 JSON，前端可直接使用渲染交互式图表。")
    public String createLineChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "X轴数据，用逗号分隔，如: 1月,2月,3月,4月") String xAxisData,
            @ToolParam(description = "Y轴数据，用逗号分隔，如: 120,200,150,180") String yAxisData,
            @ToolParam(description = "数据系列名称") String seriesName) {

        log.info("📈 生成折线图: {}", title);

        String[] xData = xAxisData.split(",");
        String[] yData = yAxisData.split(",");

        Map<String, Object> chart = buildBaseChart(title, "line");
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "category");
        xAxis.put("data", Arrays.asList(xData));
        xAxis.put("boundaryGap", false);

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new LinkedHashMap<>();
        seriesItem.put("name", seriesName);
        seriesItem.put("type", "line");
        seriesItem.put("data", Arrays.asList(yData));
        seriesItem.put("smooth", true);
        seriesItem.put("areaStyle", new LinkedHashMap<>());
        series.add(seriesItem);

        chart.put("xAxis", xAxis);
        chart.put("yAxis", yAxis);
        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成柱状图配置
     *
     * @param title      图表标题
     * @param xAxisData  X轴分类数据（用逗号分隔）
     * @param yAxisData  Y轴数值数据（用逗号分隔）
     * @param seriesName 数据系列名称
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成柱状图。适用于比较不同类别的数据，如销售额对比、用户数量排名等。返回 ECharts 配置 JSON。")
    public String createBarChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "X轴分类数据，用逗号分隔，如: 苹果,香蕉,橙子") String xAxisData,
            @ToolParam(description = "Y轴数值数据，用逗号分隔，如: 120,200,150") String yAxisData,
            @ToolParam(description = "数据系列名称") String seriesName) {

        log.info("📊 生成柱状图: {}", title);

        String[] xData = xAxisData.split(",");
        String[] yData = yAxisData.split(",");

        Map<String, Object> chart = buildBaseChart(title, "bar");
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "category");
        xAxis.put("data", Arrays.asList(xData));

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new LinkedHashMap<>();
        seriesItem.put("name", seriesName);
        seriesItem.put("type", "bar");
        seriesItem.put("data", Arrays.asList(yData));
        seriesItem.put("itemStyle", Map.of("color", "#5470c6"));
        series.add(seriesItem);

        chart.put("xAxis", xAxis);
        chart.put("yAxis", yAxis);
        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成饼图配置
     *
     * @param title    图表标题
     * @param data     饼图数据，格式: "名称:数值,名称:数值"，如: "苹果:120,香蕉:200,橙子:150"
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成饼图。适用于显示占比数据，如市场份额、投票结果、分类统计等。返回 ECharts 配置 JSON。")
    public String createPieChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "饼图数据，格式: 名称:数值,名称:数值，如: 苹果:120,香蕉:200,橙子:150") String data) {

        log.info("🥧 生成饼图: {}", title);

        Map<String, Object> chart = buildBaseChart(title, "pie");
        chart.remove("grid");

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new LinkedHashMap<>();
        seriesItem.put("name", title);
        seriesItem.put("type", "pie");
        seriesItem.put("radius", "50%");
        seriesItem.put("data", parsePieData(data));
        seriesItem.put("emphasis", Map.of(
                "itemStyle", Map.of(
                        "shadowBlur", 10,
                        "shadowOffsetX", 0,
                        "shadowColor", "rgba(0, 0, 0, 0.5)"
                )
        ));
        series.add(seriesItem);

        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成多系列折线图
     *
     * @param title         图表标题
     * @param xAxisData     X轴数据（用逗号分隔）
     * @param series1Data   系列1数据（用逗号分隔）
     * @param series1Name   系列1名称
     * @param series2Data   系列2数据（用逗号分隔）
     * @param series2Name   系列2名称
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成多系列折线图。适用于比较多个数据系列的趋势，如对比不同产品的销售趋势。返回 ECharts 配置 JSON。")
    public String createMultiLineChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "X轴数据，用逗号分隔") String xAxisData,
            @ToolParam(description = "系列1数据，用逗号分隔") String series1Data,
            @ToolParam(description = "系列1名称") String series1Name,
            @ToolParam(description = "系列2数据，用逗号分隔") String series2Data,
            @ToolParam(description = "系列2名称") String series2Name) {

        log.info("📈 生成多系列折线图: {}", title);

        String[] xData = xAxisData.split(",");
        String[] s1Data = series1Data.split(",");
        String[] s2Data = series2Data.split(",");

        Map<String, Object> chart = buildBaseChart(title, "line");
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "category");
        xAxis.put("data", Arrays.asList(xData));
        xAxis.put("boundaryGap", false);

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");

        List<Map<String, Object>> series = new ArrayList<>();

        // 系列1
        Map<String, Object> seriesItem1 = new LinkedHashMap<>();
        seriesItem1.put("name", series1Name);
        seriesItem1.put("type", "line");
        seriesItem1.put("data", Arrays.asList(s1Data));
        seriesItem1.put("smooth", true);
        series.add(seriesItem1);

        // 系列2
        Map<String, Object> seriesItem2 = new LinkedHashMap<>();
        seriesItem2.put("name", series2Name);
        seriesItem2.put("type", "line");
        seriesItem2.put("data", Arrays.asList(s2Data));
        seriesItem2.put("smooth", true);
        series.add(seriesItem2);

        chart.put("xAxis", xAxis);
        chart.put("yAxis", yAxis);
        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成散点图
     *
     * @param title      图表标题
     * @param xAxisName  X轴名称
     * @param yAxisName  Y轴名称
     * @param dataPoints 散点数据，格式: "x,y,x,y"，如: "10,20,15,25,8,18"
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成散点图。适用于显示两个变量之间的相关性，如身高体重关系、温度湿度关系等。返回 ECharts 配置 JSON。")
    public String createScatterChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "X轴名称") String xAxisName,
            @ToolParam(description = "Y轴名称") String yAxisName,
            @ToolParam(description = "散点数据，格式: x,y,x,y，如: 10,20,15,25,8,18") String dataPoints) {

        log.info("🔵 生成散点图: {}", title);

        Map<String, Object> chart = buildBaseChart(title, "scatter");
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "value");
        xAxis.put("name", xAxisName);
        xAxis.put("scale", true);

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");
        yAxis.put("name", yAxisName);
        yAxis.put("scale", true);

        List<List<Double>> points = parseScatterData(dataPoints);

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new LinkedHashMap<>();
        seriesItem.put("type", "scatter");
        seriesItem.put("symbolSize", 10);
        seriesItem.put("data", points);
        series.add(seriesItem);

        chart.put("xAxis", xAxis);
        chart.put("yAxis", yAxis);
        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成堆叠柱状图
     *
     * @param title      图表标题
     * @param categories 分类数据（用逗号分隔）
     * @param seriesData 堆叠数据，格式: "系列名:数值,数值,数值;系列名:数值,数值,数值"
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成堆叠柱状图。适用于显示部分与整体的关系，如各部门季度业绩对比。返回 ECharts 配置 JSON。")
    public String createStackedBarChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "分类数据，用逗号分隔，如: Q1,Q2,Q3,Q4") String categories,
            @ToolParam(description = "堆叠数据，格式: 系列名:数值,数值,数值;系列名:数值,数值,数值，如: 东部:120,150,180,200;西部:80,100,120,140") String seriesData) {

        log.info("📊 生成堆叠柱状图: {}", title);

        String[] cats = categories.split(",");
        String[] seriesArray = seriesData.split(";");

        Map<String, Object> chart = buildBaseChart(title, "bar");
        Map<String, Object> xAxis = new LinkedHashMap<>();
        xAxis.put("type", "category");
        xAxis.put("data", Arrays.asList(cats));

        Map<String, Object> yAxis = new LinkedHashMap<>();
        yAxis.put("type", "value");

        List<Map<String, Object>> series = new ArrayList<>();
        String[] colors = {"#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de"};

        for (int i = 0; i < seriesArray.length; i++) {
            String[] parts = seriesArray[i].split(":");
            if (parts.length != 2) continue;

            String name = parts[0].trim();
            String[] values = parts[1].split(",");

            Map<String, Object> seriesItem = new LinkedHashMap<>();
            seriesItem.put("name", name);
            seriesItem.put("type", "bar");
            seriesItem.put("stack", "total");
            seriesItem.put("data", Arrays.asList(values));
            seriesItem.put("itemStyle", Map.of("color", colors[i % colors.length]));
            series.add(seriesItem);
        }

        chart.put("xAxis", xAxis);
        chart.put("yAxis", yAxis);
        chart.put("series", series);

        return toJson(chart);
    }

    /**
     * 生成雷达图
     *
     * @param title       图表标题
     * @param indicators  雷达指标（用逗号分隔）
     * @param values      指标数值（用逗号分隔）
     * @param maxValues   指标最大值（用逗号分隔）
     * @return ECharts 配置 JSON
     */
    @Tool(description = "生成雷达图。适用于多维度对比分析，如员工能力评估、产品特性对比等。返回 ECharts 配置 JSON。")
    public String createRadarChart(
            @ToolParam(description = "图表标题") String title,
            @ToolParam(description = "雷达指标，用逗号分隔，如: 速度,耐力,技巧,防守,进攻") String indicators,
            @ToolParam(description = "指标数值，用逗号分隔，如: 85,90,78,88,92") String values,
            @ToolParam(description = "指标最大值，用逗号分隔，如: 100,100,100,100,100") String maxValues) {

        log.info("📡 生成雷达图: {}", title);

        String[] indNames = indicators.split(",");
        String[] vals = values.split(",");
        String[] maxs = maxValues.split(",");

        Map<String, Object> chart = buildBaseChart(title, "radar");

        List<Map<String, Object>> indicatorList = new ArrayList<>();
        for (int i = 0; i < indNames.length; i++) {
            Map<String, Object> ind = new LinkedHashMap<>();
            ind.put("name", indNames[i].trim());
            ind.put("max", Double.parseDouble(maxs[i].trim()));
            indicatorList.add(ind);
        }

        Map<String, Object> radar = new LinkedHashMap<>();
        radar.put("indicator", indicatorList);

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new LinkedHashMap<>();
        seriesItem.put("value", Arrays.asList(vals));
        seriesItem.put("name", title);
        seriesItem.put("type", "radar");
        series.add(seriesItem);

        chart.put("radar", radar);
        chart.put("series", series);

        return toJson(chart);
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> buildBaseChart(String title, String chartType) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("title", Map.of(
                "text", title,
                "left", "center"
        ));

        Map<String, Object> tooltip = new LinkedHashMap<>();
        tooltip.put("trigger", "item");
        chart.put("tooltip", tooltip);

        Map<String, Object> legend = new LinkedHashMap<>();
        legend.put("bottom", 10);
        chart.put("legend", legend);

        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("left", "3%");
        grid.put("right", "4%");
        grid.put("bottom", "15%");
        grid.put("containLabel", true);
        chart.put("grid", grid);

        chart.put("chartType", chartType);

        return chart;
    }

    private List<Map<String, Object>> parsePieData(String data) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] items = data.split(",");
        String[] colors = {"#5470c6", "#91cc75", "#fac858", "#ee6666", "#73c0de", "#3ba272", "#fc8452", "#9a60b4"};

        for (int i = 0; i < items.length; i++) {
            String[] parts = items[i].split(":");
            if (parts.length != 2) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", parts[0].trim());
            item.put("value", Double.parseDouble(parts[1].trim()));
            item.put("itemStyle", Map.of("color", colors[i % colors.length]));
            result.add(item);
        }

        return result;
    }

    private List<List<Double>> parseScatterData(String dataPoints) {
        List<List<Double>> result = new ArrayList<>();
        String[] points = dataPoints.split(",");

        for (int i = 0; i + 1 < points.length; i += 2) {
            List<Double> point = new ArrayList<>();
            point.add(Double.parseDouble(points[i].trim()));
            point.add(Double.parseDouble(points[i + 1].trim()));
            result.add(point);
        }

        return result;
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if ("chartType".equals(entry.getKey())) continue;
            json.append("  \"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (++count < map.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List) {
            return formatList((List<?>) value);
        }
        if (value instanceof Map) {
            return formatMap((Map<?, ?>) value);
        }
        return "\"" + value.toString() + "\"";
    }

    private String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append("    ").append(formatValue(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private String formatMap(Map<?, ?> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ");
            sb.append(formatValue(entry.getValue()));
            if (++i < map.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }
}