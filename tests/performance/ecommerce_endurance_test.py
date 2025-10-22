"""
⏰ Endurance Testing - Long-running sustained load
Tests system stability and resource leaks over extended periods
"""

from locust import HttpUser, task, between
import random
import time
from user_behavior import RealisticEcommerceUser

class EnduranceTestUser(RealisticEcommerceUser):
    """
    Endurance testing user for long-running tests
    Target: 150 users for 1 hour sustained load
    """
    
    wait_time = between(2, 6)  # Moderate pace for endurance
    weight = 1
    
    @task(3)
    def sustained_browsing(self):
        """Steady browsing activity over time"""
        self.browse_products()
        
        # Occasional search activity
        if random.random() < 0.4:
            self.search_products()
    
    @task(2)
    def periodic_user_activity(self):
        """Regular user activities over time"""
        if not self.user_id:
            if self.register_user():
                # Initial burst of activity after registration
                for _ in range(random.randint(2, 4)):
                    self.browse_products()
        else:
            # Regular user behavior
            self.browse_products()
            
            # Periodic purchases (lower frequency for endurance)
            if random.random() < 0.15:
                if self.create_cart():
                    self.place_order()
    
    @task(1)
    def long_term_user_behavior(self):
        """Behavior patterns for long-term users"""
        if not self.user_id:
            self.register_user()
        
        # Check order history periodically
        if self.created_orders and random.random() < 0.3:
            self.get_user_orders()
        
        # Steady browsing
        self.browse_products()
        
        # Occasional cart creation (session-like behavior)
        if random.random() < 0.2:
            self.create_cart()

class SessionBasedUser(RealisticEcommerceUser):
    """User with session-like behavior for endurance testing"""
    
    wait_time = between(1, 4)
    weight = 1
    
    def on_start(self):
        """Start a user session"""
        super().on_start()
        self.session_start_time = time.time()
        self.session_actions = 0
        self.max_session_actions = random.randint(10, 30)
    
    @task(4)
    def session_activity(self):
        """Activity within a user session"""
        if self.session_actions >= self.max_session_actions:
            # End session and start a new one
            self.start_new_session()
            return
        
        # Normal session activity
        if not self.user_id:
            self.register_user()
        
        action = random.choice(['browse', 'search', 'cart', 'order'])
        
        if action == 'browse':
            self.browse_products()
        elif action == 'search':
            self.search_products()
        elif action == 'cart':
            self.create_cart()
        elif action == 'order' and self.cart_id:
            self.place_order()
        
        self.session_actions += 1
    
    def start_new_session(self):
        """Start a new user session"""
        print(f"User {self.get_user_id()}: Starting new session")
        self.session_start_time = time.time()
        self.session_actions = 0
        self.max_session_actions = random.randint(10, 30)
        
        # Reset some session data
        self.cart_id = None

class LoyalCustomerUser(RealisticEcommerceUser):
    """Loyal customer behavior over extended periods"""
    
    wait_time = between(3, 8)  # More leisurely browsing
    weight = 1
    
    @task(3)
    def loyal_browsing(self):
        """Loyal customers browse thoroughly"""
        # More thorough browsing
        for _ in range(random.randint(2, 5)):
            self.browse_products()
            time.sleep(random.uniform(1, 3))  # More deliberate browsing
    
    @task(2)
    def repeat_purchases(self):
        """Loyal customers make repeat purchases"""
        if not self.user_id:
            self.register_user()
        
        # Higher purchase probability for loyal customers
        if random.random() < 0.4:
            if self.create_cart():
                self.place_order()
    
    @task(1)
    def check_order_history(self):
        """Loyal customers check their order history"""
        if self.user_id and self.created_orders:
            self.get_user_orders()

class BackgroundMaintenanceUser(RealisticEcommerceUser):
    """Simulates background maintenance operations"""
    
    wait_time = between(10, 30)  # Low frequency background operations
    weight = 1
    
    @task(1)
    def maintenance_operations(self):
        """Simulate maintenance-like operations"""
        # Periodic system health checks
        with self.client.get("/user-service/actuator/health",
                           name="Health Check - User Service") as response:
            pass
        
        with self.client.get("/product-service/actuator/health", 
                           name="Health Check - Product Service") as response:
            pass
        
        with self.client.get("/order-service/actuator/health",
                           name="Health Check - Order Service") as response:
            pass

class EcommerceEnduranceTestUser(HttpUser):
    """
    Main endurance test user class
    Tests system stability over extended periods
    """
    
    # Distribution optimized for endurance testing
    tasks = {
        EnduranceTestUser: 40,        # 40% standard endurance users
        SessionBasedUser: 30,         # 30% session-based users
        LoyalCustomerUser: 25,        # 25% loyal customers
        BackgroundMaintenanceUser: 5   # 5% background operations
    }
    
    wait_time = between(2, 6)
    
    def on_start(self):
        """Initialize endurance test user"""
        print(f"⏰ Endurance Test User {self.get_user_id()} started")
    
    def on_stop(self):
        """Clean up endurance test user"""
        print(f"⏰ Endurance Test User {self.get_user_id()} stopped")
    
    def get_user_id(self):
        """Get unique user identifier for logging"""
        return getattr(self, '_user_id', id(self))

# Custom event handlers for endurance testing
from locust import events
import datetime

# Global variables for endurance test tracking
endurance_start_time = None
endurance_stats_log = []

@events.test_start.add_listener
def on_endurance_test_start(environment, **kwargs):
    """Called when endurance test starts"""
    global endurance_start_time
    endurance_start_time = time.time()
    
    print("⏰ ENDURANCE TEST STARTED")
    print("Duration: 1 hour sustained load")
    print("Focus: System stability, memory leaks, resource management")
    print("Watch for: Gradual performance degradation, error accumulation")

@events.spawning_complete.add_listener
def on_endurance_spawning_complete(environment, **kwargs):
    """Called when target user count is reached"""
    print(f"⏰ ENDURANCE LOAD ESTABLISHED: {environment.runner.user_count} users")
    print("Beginning sustained load monitoring...")

# Periodic stats collection during endurance test
def collect_endurance_stats(environment):
    """Collect stats periodically during endurance test"""
    global endurance_stats_log
    
    current_time = time.time()
    elapsed_minutes = (current_time - endurance_start_time) / 60
    
    stats = environment.stats
    
    stats_entry = {
        'elapsed_minutes': elapsed_minutes,
        'rps': stats.total.current_rps,
        'avg_response_time': stats.total.avg_response_time,
        'error_rate': stats.total.fail_ratio,
        'total_requests': stats.total.num_requests,
        'total_failures': stats.total.num_failures
    }
    
    endurance_stats_log.append(stats_entry)
    
    print(f"⏰ Endurance Check ({elapsed_minutes:.1f}min): "
          f"RPS={stats_entry['rps']:.1f}, "
          f"AvgRT={stats_entry['avg_response_time']:.0f}ms, "
          f"Errors={stats_entry['error_rate']:.2%}")

# Set up periodic stats collection
import threading

def start_endurance_monitoring(environment):
    """Start periodic endurance monitoring"""
    def monitor():
        while environment.runner.state != "stopped":
            time.sleep(300)  # Collect stats every 5 minutes
            if environment.runner.state == "spawning" or environment.runner.state == "running":
                collect_endurance_stats(environment)
    
    monitor_thread = threading.Thread(target=monitor, daemon=True)
    monitor_thread.start()

@events.spawning_complete.add_listener
def start_monitoring(environment, **kwargs):
    """Start monitoring after spawning is complete"""
    start_endurance_monitoring(environment)

@events.test_stop.add_listener
def on_endurance_test_stop(environment, **kwargs):
    """Called when endurance test stops"""
    print("⏰ ENDURANCE TEST COMPLETED")
    
    # Final analysis
    total_duration = time.time() - endurance_start_time
    duration_hours = total_duration / 3600
    
    stats = environment.stats
    final_rps = stats.total.current_rps
    final_avg_rt = stats.total.avg_response_time
    final_error_rate = stats.total.fail_ratio
    total_requests = stats.total.num_requests
    
    print(f"\n⏰ ENDURANCE TEST SUMMARY:")
    print(f"Total Duration: {duration_hours:.2f} hours")
    print(f"Total Requests: {total_requests:,}")
    print(f"Final RPS: {final_rps:.2f}")
    print(f"Final Avg Response Time: {final_avg_rt:.2f}ms")
    print(f"Final Error Rate: {final_error_rate:.2%}")
    
    # Analyze trends over time
    if len(endurance_stats_log) > 2:
        initial_rt = endurance_stats_log[0]['avg_response_time']
        final_rt = endurance_stats_log[-1]['avg_response_time']
        rt_degradation = ((final_rt - initial_rt) / initial_rt) * 100
        
        initial_error_rate = endurance_stats_log[0]['error_rate']
        final_error_rate_logged = endurance_stats_log[-1]['error_rate']
        error_trend = final_error_rate_logged - initial_error_rate
        
        print(f"\n📊 TREND ANALYSIS:")
        print(f"Response Time Change: {rt_degradation:+.1f}%")
        print(f"Error Rate Change: {error_trend:+.2%}")
        
        # Endurance test evaluation
        if rt_degradation < 20 and error_trend < 0.02:
            print("✅ Excellent endurance performance - no significant degradation")
        elif rt_degradation < 50 and error_trend < 0.05:
            print("⚠️ Acceptable endurance performance - minor degradation observed")
        else:
            print("❌ Poor endurance performance - significant degradation detected")
            print("🔍 Investigate: Memory leaks, connection pool issues, database performance")

if __name__ == "__main__":
    """
    Run this endurance test with:
    
    # 1-hour endurance test
    locust -f ecommerce_endurance_test.py --host=http://localhost:8100 --users 150 --spawn-rate 5 --run-time 1h --headless
    
    # Extended 2-hour test for thorough endurance testing
    locust -f ecommerce_endurance_test.py --host=http://localhost:8100 --users 200 --spawn-rate 3 --run-time 2h --headless
    
    Expected Behavior:
    - System should maintain stable performance over time
    - Response times should not degrade significantly
    - Error rates should remain low and stable
    - Memory usage should not continuously increase
    
    Key Metrics to Monitor:
    - Response time stability over time
    - Error rate trends
    - Memory usage patterns
    - Database connection health
    - Resource utilization trends
    
    Warning Signs:
    - Gradual response time increase
    - Increasing error rates over time
    - Memory leaks
    - Database connection exhaustion
    - File handle leaks
    """
    pass