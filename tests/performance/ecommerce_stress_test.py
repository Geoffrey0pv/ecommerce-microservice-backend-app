"""
🔥 Stress Testing - Push system to its limits
Gradually ramp up to 500 users to find breaking points
"""

from locust import HttpUser, task, between
import random
from user_behavior import RealisticEcommerceUser

class StressTestUser(RealisticEcommerceUser):
    """
    Stress testing user with aggressive behavior
    Target: Ramp up to 500 users over 15 minutes
    """
    
    wait_time = between(0.5, 2)  # Aggressive timing
    weight = 1
    
    @task(3)
    def aggressive_browsing(self):
        """Rapid browsing with minimal think time"""
        self.browse_products()
        
        # Immediate follow-up action
        if random.random() < 0.5:
            self.search_products()
    
    @task(2)
    def rapid_user_flow(self):
        """Rapid user registration and activity"""
        if not self.user_id:
            self.register_user()
        
        # Quick sequence of actions
        self.browse_products()
        
        if random.random() < 0.3:
            if self.create_cart():
                self.place_order()
    
    @task(2)
    def concurrent_operations(self):
        """Simulate concurrent operations stress"""
        if not self.user_id:
            self.register_user()
        
        # Multiple rapid requests
        for _ in range(random.randint(2, 4)):
            if random.random() < 0.5:
                self.browse_products()
            else:
                self.search_products()
    
    @task(1)
    def stress_database_operations(self):
        """Operations that stress the database"""
        if not self.user_id:
            self.register_user()
        
        # Multiple cart creations and orders
        for _ in range(random.randint(1, 3)):
            if self.create_cart():
                self.place_order()
            
            # Check orders frequently
            if self.created_orders:
                self.get_user_orders()

class HighFrequencyUser(RealisticEcommerceUser):
    """User with very high request frequency"""
    
    wait_time = between(0.1, 0.5)  # Very fast interactions
    weight = 1
    
    @task(4)
    def rapid_fire_requests(self):
        """Rapid fire product requests"""
        for _ in range(random.randint(3, 6)):
            self.browse_products()
    
    @task(2)
    def burst_activity(self):
        """Burst of activity in short time"""
        if not self.user_id:
            self.register_user()
        
        # Burst of searches
        for search_term in ["laptop", "phone", "book"]:
            with self.client.get(f"/product-service/api/products?search={search_term}",
                               name="Burst Search") as response:
                pass
    
    @task(1)
    def stress_order_system(self):
        """Stress the order processing system"""
        if not self.user_id:
            self.register_user()
        
        # Rapid cart creation and abandonment
        for _ in range(random.randint(2, 5)):
            self.create_cart()

class DatabaseStressUser(RealisticEcommerceUser):
    """User focused on database-intensive operations"""
    
    wait_time = between(0.2, 1)
    weight = 1
    
    @task(3)
    def heavy_read_operations(self):
        """Heavy read operations"""
        # Multiple product list requests
        for _ in range(random.randint(5, 10)):
            self.get_products()
    
    @task(2)
    def heavy_write_operations(self):
        """Heavy write operations"""
        if not self.user_id:
            self.register_user()
        
        # Multiple writes
        for _ in range(random.randint(3, 6)):
            if self.create_cart():
                self.place_order()
    
    @task(1)
    def mixed_operations(self):
        """Mix of read and write operations"""
        if not self.user_id:
            self.register_user()
        
        # Interleaved reads and writes
        for _ in range(random.randint(4, 8)):
            if random.random() < 0.5:
                self.browse_products()  # Read
            else:
                self.create_cart()      # Write

class EcommerceStressTestUser(HttpUser):
    """
    Main stress test user class
    Combines different stress patterns for comprehensive testing
    """
    
    # Distribution optimized for stress testing
    tasks = {
        StressTestUser: 40,        # 40% normal stress users
        HighFrequencyUser: 30,     # 30% high-frequency users
        DatabaseStressUser: 30     # 30% database stress users
    }
    
    wait_time = between(0.5, 2)
    
    def on_start(self):
        """Initialize stress test user"""
        print(f"🔥 Stress Test User {self.get_user_id()} started")
    
    def on_stop(self):
        """Clean up stress test user"""
        print(f"🔥 Stress Test User {self.get_user_id()} stopped")
    
    def get_user_id(self):
        """Get unique user identifier for logging"""
        return getattr(self, '_user_id', id(self))

# Custom Locust event handlers for stress testing
from locust import events

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Called when stress test starts"""
    print("🔥 STRESS TEST STARTED")
    print("Target: Ramp up to 500 users")
    print("Expected behavior: System should handle increasing load gracefully")
    print("Watch for: Response time degradation, error rate increase, resource exhaustion")

@events.test_stop.add_listener  
def on_test_stop(environment, **kwargs):
    """Called when stress test stops"""
    print("🔥 STRESS TEST COMPLETED")
    
    # Get final stats
    stats = environment.stats
    total_rps = stats.total.current_rps
    avg_response_time = stats.total.avg_response_time
    error_rate = stats.total.fail_ratio
    
    print(f"Final RPS: {total_rps:.2f}")
    print(f"Average Response Time: {avg_response_time:.2f}ms")
    print(f"Error Rate: {error_rate:.2%}")
    
    # Determine stress test results
    if error_rate < 0.05 and avg_response_time < 3000:
        print("✅ System handled stress well")
    elif error_rate < 0.10 and avg_response_time < 5000:
        print("⚠️ System showed stress but remained functional")
    else:
        print("❌ System showed significant stress symptoms")

if __name__ == "__main__":
    """
    Run this stress test with:
    
    locust -f ecommerce_stress_test.py --host=http://localhost:8100 --users 500 --spawn-rate 20 --run-time 15m --headless
    
    Expected Behavior:
    - Response times should increase gradually
    - Error rate may increase at higher loads  
    - System should not crash completely
    - Resource utilization should reach limits
    
    Key Metrics to Watch:
    - Response Time: Monitor P95 and P99 percentiles
    - Error Rate: Should stay under 10% if possible
    - Throughput: Peak sustainable RPS
    - System Resources: CPU, Memory, Database connections
    """
    pass