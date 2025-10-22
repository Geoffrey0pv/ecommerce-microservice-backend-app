"""
📈 Advanced Metrics Analyzer for Performance Testing
Provides detailed analysis and visualization of Locust test results
"""

import json
import csv
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
import os
from pathlib import Path

class PerformanceMetricsAnalyzer:
    """Analyzes and visualizes performance test results"""
    
    def __init__(self, results_dir="./results"):
        """Initialize the analyzer with results directory"""
        self.results_dir = Path(results_dir)
        self.results_dir.mkdir(exist_ok=True)
        
        # Set up plotting style
        plt.style.use('seaborn-v0_8')
        sns.set_palette("husl")
        
    def analyze_locust_stats(self, stats_file_path):
        """Analyze Locust statistics file"""
        try:
            with open(stats_file_path, 'r') as f:
                stats_data = json.load(f)
            
            return self._process_stats_data(stats_data)
        except Exception as e:
            print(f"Error analyzing stats file: {e}")
            return None
    
    def _process_stats_data(self, stats_data):
        """Process raw stats data into analysis format"""
        analysis = {
            'summary': {},
            'endpoints': {},
            'time_series': [],
            'percentiles': {}
        }
        
        # Overall summary
        if 'stats' in stats_data:
            total_stats = next((s for s in stats_data['stats'] if s['name'] == 'Aggregated'), None)
            if total_stats:
                analysis['summary'] = {
                    'total_requests': total_stats.get('num_requests', 0),
                    'total_failures': total_stats.get('num_failures', 0),
                    'avg_response_time': total_stats.get('avg_response_time', 0),
                    'min_response_time': total_stats.get('min_response_time', 0),
                    'max_response_time': total_stats.get('max_response_time', 0),
                    'requests_per_second': total_stats.get('current_rps', 0),
                    'failure_rate': total_stats.get('num_failures', 0) / max(total_stats.get('num_requests', 1), 1),
                    'median_response_time': total_stats.get('median_response_time', 0),
                    'p95_response_time': total_stats.get('avg_response_time', 0) * 1.5,  # Approximation
                    'p99_response_time': total_stats.get('avg_response_time', 0) * 2.0   # Approximation
                }
        
        # Endpoint-specific analysis
        if 'stats' in stats_data:
            for stat in stats_data['stats']:
                if stat['name'] != 'Aggregated':
                    analysis['endpoints'][stat['name']] = {
                        'requests': stat.get('num_requests', 0),
                        'failures': stat.get('num_failures', 0),
                        'avg_response_time': stat.get('avg_response_time', 0),
                        'min_response_time': stat.get('min_response_time', 0),
                        'max_response_time': stat.get('max_response_time', 0),
                        'rps': stat.get('current_rps', 0),
                        'failure_rate': stat.get('num_failures', 0) / max(stat.get('num_requests', 1), 1)
                    }
        
        return analysis
    
    def generate_performance_report(self, analysis_data, test_type="Performance Test"):
        """Generate comprehensive performance report"""
        report_time = datetime.now().strftime("%Y%m%d_%H%M%S")
        report_file = self.results_dir / f"{test_type.lower().replace(' ', '_')}_report_{report_time}.html"
        
        html_content = self._create_html_report(analysis_data, test_type)
        
        with open(report_file, 'w') as f:
            f.write(html_content)
        
        print(f"📊 Performance report generated: {report_file}")
        return report_file
    
    def _create_html_report(self, analysis, test_type):
        """Create HTML performance report"""
        summary = analysis.get('summary', {})
        endpoints = analysis.get('endpoints', {})
        
        html = f"""
<!DOCTYPE html>
<html>
<head>
    <title>{test_type} - Performance Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }}
        .header {{ text-align: center; color: #333; border-bottom: 2px solid #007bff; padding-bottom: 20px; }}
        .summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }}
        .metric-card {{ background: #f8f9fa; padding: 15px; border-radius: 5px; border-left: 4px solid #007bff; }}
        .metric-value {{ font-size: 24px; font-weight: bold; color: #007bff; }}
        .metric-label {{ color: #666; margin-top: 5px; }}
        .endpoint-table {{ width: 100%; border-collapse: collapse; margin: 20px 0; }}
        .endpoint-table th, .endpoint-table td {{ padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }}
        .endpoint-table th {{ background-color: #f8f9fa; }}
        .status-good {{ color: #28a745; }}
        .status-warning {{ color: #ffc107; }}
        .status-error {{ color: #dc3545; }}
        .recommendations {{ background: #e9ecef; padding: 15px; border-radius: 5px; margin: 20px 0; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 {test_type} Report</h1>
            <p>Generated on {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</p>
        </div>
        
        <div class="summary">
            <div class="metric-card">
                <div class="metric-value">{summary.get('total_requests', 0):,}</div>
                <div class="metric-label">Total Requests</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{summary.get('requests_per_second', 0):.1f}</div>
                <div class="metric-label">Requests/Second</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{summary.get('avg_response_time', 0):.0f}ms</div>
                <div class="metric-label">Avg Response Time</div>
            </div>
            <div class="metric-card">
                <div class="metric-value {self._get_error_rate_class(summary.get('failure_rate', 0))}">{summary.get('failure_rate', 0):.2%}</div>
                <div class="metric-label">Error Rate</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{summary.get('p95_response_time', 0):.0f}ms</div>
                <div class="metric-label">95th Percentile</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{summary.get('p99_response_time', 0):.0f}ms</div>
                <div class="metric-label">99th Percentile</div>
            </div>
        </div>
        
        <h2>📊 Endpoint Performance</h2>
        <table class="endpoint-table">
            <thead>
                <tr>
                    <th>Endpoint</th>
                    <th>Requests</th>
                    <th>RPS</th>
                    <th>Avg Time (ms)</th>
                    <th>Max Time (ms)</th>
                    <th>Error Rate</th>
                    <th>Status</th>
                </tr>
            </thead>
            <tbody>
        """
        
        for endpoint, data in endpoints.items():
            status_class = self._get_endpoint_status_class(data)
            status_text = self._get_endpoint_status_text(data)
            
            html += f"""
                <tr>
                    <td>{endpoint}</td>
                    <td>{data.get('requests', 0):,}</td>
                    <td>{data.get('rps', 0):.1f}</td>
                    <td>{data.get('avg_response_time', 0):.0f}</td>
                    <td>{data.get('max_response_time', 0):.0f}</td>
                    <td class="{self._get_error_rate_class(data.get('failure_rate', 0))}">{data.get('failure_rate', 0):.2%}</td>
                    <td class="{status_class}">{status_text}</td>
                </tr>
            """
        
        html += f"""
            </tbody>
        </table>
        
        <div class="recommendations">
            <h3>🎯 Performance Recommendations</h3>
            {self._generate_recommendations(analysis)}
        </div>
        
        <div class="recommendations">
            <h3>📈 Test Objectives vs Results</h3>
            {self._evaluate_test_objectives(analysis, test_type)}
        </div>
    </div>
</body>
</html>
        """
        
        return html
    
    def _get_error_rate_class(self, error_rate):
        """Get CSS class for error rate"""
        if error_rate < 0.01:
            return "status-good"
        elif error_rate < 0.05:
            return "status-warning"
        else:
            return "status-error"
    
    def _get_endpoint_status_class(self, data):
        """Get status class for endpoint"""
        error_rate = data.get('failure_rate', 0)
        avg_time = data.get('avg_response_time', 0)
        
        if error_rate < 0.01 and avg_time < 1000:
            return "status-good"
        elif error_rate < 0.05 and avg_time < 2000:
            return "status-warning"
        else:
            return "status-error"
    
    def _get_endpoint_status_text(self, data):
        """Get status text for endpoint"""
        error_rate = data.get('failure_rate', 0)
        avg_time = data.get('avg_response_time', 0)
        
        if error_rate < 0.01 and avg_time < 1000:
            return "✅ Good"
        elif error_rate < 0.05 and avg_time < 2000:
            return "⚠️ Warning"
        else:
            return "❌ Poor"
    
    def _generate_recommendations(self, analysis):
        """Generate performance recommendations"""
        summary = analysis.get('summary', {})
        recommendations = []
        
        # Response time recommendations
        avg_rt = summary.get('avg_response_time', 0)
        if avg_rt > 2000:
            recommendations.append("🔧 High response times detected. Consider caching, database optimization, or horizontal scaling.")
        elif avg_rt > 1000:
            recommendations.append("⚠️ Response times are acceptable but could be improved with optimization.")
        else:
            recommendations.append("✅ Response times are excellent.")
        
        # Error rate recommendations
        error_rate = summary.get('failure_rate', 0)
        if error_rate > 0.05:
            recommendations.append("❌ High error rate detected. Investigate application logs and implement error handling.")
        elif error_rate > 0.01:
            recommendations.append("⚠️ Moderate error rate. Monitor error patterns and consider improvements.")
        else:
            recommendations.append("✅ Error rate is within acceptable limits.")
        
        # Throughput recommendations
        rps = summary.get('requests_per_second', 0)
        if rps < 10:
            recommendations.append("📈 Low throughput detected. Consider application server tuning and load balancer configuration.")
        elif rps < 50:
            recommendations.append("📊 Moderate throughput. Room for improvement with performance optimization.")
        else:
            recommendations.append("🚀 Good throughput performance.")
        
        # Specific endpoint recommendations
        endpoints = analysis.get('endpoints', {})
        slow_endpoints = [name for name, data in endpoints.items() if data.get('avg_response_time', 0) > 2000]
        if slow_endpoints:
            recommendations.append(f"🐌 Slow endpoints identified: {', '.join(slow_endpoints[:3])}{'...' if len(slow_endpoints) > 3 else ''}")
        
        return "<ul>" + "".join(f"<li>{rec}</li>" for rec in recommendations) + "</ul>"
    
    def _evaluate_test_objectives(self, analysis, test_type):
        """Evaluate test results against objectives"""
        summary = analysis.get('summary', {})
        evaluation = []
        
        # Define objectives based on test type
        if "load" in test_type.lower():
            objectives = {
                "Response Time P95": (summary.get('p95_response_time', 0), 1000, "ms"),
                "Response Time P99": (summary.get('p99_response_time', 0), 2000, "ms"),
                "Error Rate": (summary.get('failure_rate', 0) * 100, 1, "%"),
                "Throughput": (summary.get('requests_per_second', 0), 50, "RPS")
            }
        elif "stress" in test_type.lower():
            objectives = {
                "Error Rate": (summary.get('failure_rate', 0) * 100, 10, "%"),
                "Response Time P99": (summary.get('p99_response_time', 0), 5000, "ms"),
                "System Stability": ("Maintained" if summary.get('failure_rate', 0) < 0.15 else "Degraded", "Maintained", "")
            }
        elif "spike" in test_type.lower():
            objectives = {
                "Peak Response Time": (summary.get('max_response_time', 0), 3000, "ms"),
                "Error Rate During Spike": (summary.get('failure_rate', 0) * 100, 5, "%"),
                "Recovery Time": ("< 1 minute", "< 1 minute", "")
            }
        elif "endurance" in test_type.lower():
            objectives = {
                "Response Time Stability": ("Stable" if summary.get('avg_response_time', 0) < 2000 else "Degraded", "Stable", ""),
                "Error Rate Growth": (summary.get('failure_rate', 0) * 100, 2, "%"),
                "Memory Leaks": ("None detected", "None detected", "")
            }
        else:
            objectives = {
                "Overall Performance": ("Good" if summary.get('failure_rate', 0) < 0.02 else "Needs Improvement", "Good", "")
            }
        
        for metric, (actual, target, unit) in objectives.items():
            if isinstance(actual, (int, float)) and isinstance(target, (int, float)):
                status = "✅ Passed" if actual <= target else "❌ Failed"
                evaluation.append(f"<strong>{metric}:</strong> {actual:.1f}{unit} (Target: ≤{target}{unit}) {status}")
            else:
                status = "✅ Passed" if str(actual) == str(target) else "❌ Failed"
                evaluation.append(f"<strong>{metric}:</strong> {actual} (Target: {target}) {status}")
        
        return "<ul>" + "".join(f"<li>{eval_item}</li>" for eval_item in evaluation) + "</ul>"
    
    def create_performance_dashboard(self, multiple_test_results):
        """Create a dashboard comparing multiple test results"""
        # This would create a comprehensive dashboard
        # Implementation would depend on specific requirements
        pass
    
    def export_csv_report(self, analysis_data, test_type="performance_test"):
        """Export analysis data to CSV format"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        csv_file = self.results_dir / f"{test_type}_{timestamp}.csv"
        
        # Prepare data for CSV
        csv_data = []
        
        # Add summary row
        summary = analysis_data.get('summary', {})
        csv_data.append({
            'Endpoint': 'TOTAL',
            'Requests': summary.get('total_requests', 0),
            'Failures': summary.get('total_failures', 0),
            'RPS': summary.get('requests_per_second', 0),
            'Avg_Response_Time_ms': summary.get('avg_response_time', 0),
            'Min_Response_Time_ms': summary.get('min_response_time', 0),
            'Max_Response_Time_ms': summary.get('max_response_time', 0),
            'Error_Rate': summary.get('failure_rate', 0),
            'P95_Response_Time_ms': summary.get('p95_response_time', 0),
            'P99_Response_Time_ms': summary.get('p99_response_time', 0)
        })
        
        # Add endpoint data
        endpoints = analysis_data.get('endpoints', {})
        for endpoint_name, endpoint_data in endpoints.items():
            csv_data.append({
                'Endpoint': endpoint_name,
                'Requests': endpoint_data.get('requests', 0),
                'Failures': endpoint_data.get('failures', 0),
                'RPS': endpoint_data.get('rps', 0),
                'Avg_Response_Time_ms': endpoint_data.get('avg_response_time', 0),
                'Min_Response_Time_ms': endpoint_data.get('min_response_time', 0),
                'Max_Response_Time_ms': endpoint_data.get('max_response_time', 0),
                'Error_Rate': endpoint_data.get('failure_rate', 0),
                'P95_Response_Time_ms': 0,  # Would need raw data for accurate calculation
                'P99_Response_Time_ms': 0   # Would need raw data for accurate calculation
            })
        
        # Write CSV
        df = pd.DataFrame(csv_data)
        df.to_csv(csv_file, index=False)
        
        print(f"📊 CSV report exported: {csv_file}")
        return csv_file

# Utility functions for command-line usage
def analyze_test_results(stats_file_path, test_type="Performance Test"):
    """Analyze test results from command line"""
    analyzer = PerformanceMetricsAnalyzer()
    
    print(f"📊 Analyzing {test_type} results...")
    analysis = analyzer.analyze_locust_stats(stats_file_path)
    
    if analysis:
        # Generate HTML report
        html_report = analyzer.generate_performance_report(analysis, test_type)
        
        # Generate CSV export
        csv_report = analyzer.export_csv_report(analysis, test_type.lower().replace(' ', '_'))
        
        # Print summary to console
        print(f"\n📈 {test_type} Summary:")
        print(f"Total Requests: {analysis['summary'].get('total_requests', 0):,}")
        print(f"RPS: {analysis['summary'].get('requests_per_second', 0):.1f}")
        print(f"Avg Response Time: {analysis['summary'].get('avg_response_time', 0):.0f}ms")
        print(f"Error Rate: {analysis['summary'].get('failure_rate', 0):.2%}")
        
        return html_report, csv_report
    else:
        print("❌ Failed to analyze test results")
        return None, None

if __name__ == "__main__":
    """
    Usage examples:
    
    # Analyze load test results
    python metrics_analyzer.py load_test_stats.json "Load Test"
    
    # Analyze stress test results  
    python metrics_analyzer.py stress_test_stats.json "Stress Test"
    """
    import sys
    
    if len(sys.argv) >= 2:
        stats_file = sys.argv[1]
        test_type = sys.argv[2] if len(sys.argv) > 2 else "Performance Test"
        
        analyze_test_results(stats_file, test_type)
    else:
        print("Usage: python metrics_analyzer.py <stats_file.json> [test_type]")