"""
🧪 Load Testing - Standard load testing with 100 concurrent users
Simulates normal business operations and user traffic
"""

from locust import HttpUser, task, between
import random
from user_behavior import RealisticEcommerceUser

class LoadTestUser(RealisticEcommerceUser):
    """
    Load testing with realistic user behavior
    Target: 100 users for 10 minutes
    """
    
    wait_time = between(1, 4)  # 1-4 seconds between requests
    weight = 1
    
    @task(4)
    def browse_and_search(self):
        """Most common user activity - browsing and searching"""
        if random.random() < 0.6:
            self.browse_products()
        else:
            self.search_products()
    
    @task(2)
    def user_registration_and_browsing(self):
        """New user registration and initial browsing"""
        if not self.user_id:
            if self.register_user():
                self.browse_products()
        else:
            self.browse_products()
    
    @task(1)
    def complete_purchase_flow(self):
        """Complete purchase workflow"""
        if not self.user_id:
            if not self.register_user():
                return
        
        # Browse before purchasing
        self.browse_products()
        
        # 25% chance to purchase
        if random.random() < 0.25:
            if self.create_cart():
                self.place_order()
    
    @task(1)
    def check_order_status(self):
        """Check existing orders"""
        if self.user_id and self.created_orders:
            self.get_user_orders()

# Alternative user classes for different load patterns
class PeakHourUser(RealisticEcommerceUser):
    """Simulates peak hour traffic patterns"""
    
    wait_time = between(0.5, 2)  # Faster interactions during peak
    weight = 1
    
    @task(3)
    def rapid_browsing(self):
        self.browse_products()
    
    @task(2)
    def quick_search_and_purchase(self):
        if not self.user_id:
            self.register_user()
        
        self.search_products()
        
        # Higher purchase probability during peak hours
        if random.random() < 0.4:
            if self.create_cart():
                self.place_order()

class BackgroundUser(RealisticEcommerceUser):
    """Simulates background/low-activity users"""
    
    wait_time = between(3, 8)  # Slower interactions
    weight = 1
    
    @task(5)
    def casual_browsing(self):
        self.browse_products()
    
    @task(1)
    def occasional_search(self):
        self.search_products()

# Main user class for load testing
class EcommerceLoadTestUser(HttpUser):
    """
    Main user class that combines different user behaviors
    for realistic load testing scenarios
    """
    
    # Mix of user types for realistic load distribution
    tasks = {
        LoadTestUser: 60,      # 60% normal users
        PeakHourUser: 25,      # 25% peak hour users  
        BackgroundUser: 15     # 15% background users
    }
    
    wait_time = between(1, 3)
    
    def on_start(self):
        """Initialize user session"""
        print(f"🧪 Load Test User {self.get_user_id()} started")
    
    def on_stop(self):
        """Clean up user session"""
        print(f"🧪 Load Test User {self.get_user_id()} stopped")
    
    def get_user_id(self):
        """Get unique user identifier for logging"""
        return getattr(self, '_user_id', id(self))

if __name__ == "__main__":
    """
    Run this load test with:
    
    locust -f ecommerce_load_test.py --host=http://localhost:8100 --users 100 --spawn-rate 10 --run-time 10m --headless
    
    Expected Metrics:
    - Response Time: P95 < 1000ms, P99 < 2000ms
    - Throughput: 50-100 RPS
    - Error Rate: < 2%
    - Users: 100 concurrent users
    """
    pass