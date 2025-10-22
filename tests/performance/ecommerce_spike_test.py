"""
📊 Spike Testing - Sudden traffic spikes simulation
Tests system behavior under sudden load increases
"""

from locust import HttpUser, task, between
import random
import time
from user_behavior import RealisticEcommerceUser

class SpikeTestUser(RealisticEcommerceUser):
    """
    Spike testing user for sudden traffic bursts
    Target: Quick ramp to 300 users, hold, then drop
    """
    
    wait_time = between(0.3, 1.5)  # Fast interactions during spike
    weight = 1
    
    @task(4)
    def spike_browsing(self):
        """Intensive browsing during traffic spike"""
        # Rapid product browsing
        self.browse_products()
        
        # Quick follow-up searches
        if random.random() < 0.6:
            self.search_products()
    
    @task(3)
    def spike_user_registration(self):
        """Mass user registrations during spike"""
        if not self.user_id:
            if self.register_user():
                # Immediate activity after registration
                self.browse_products()
                
                # Higher chance of immediate purchase during spike
                if random.random() < 0.4:
                    if self.create_cart():
                        self.place_order()
    
    @task(2)
    def spike_order_activity(self):
        """Increased order activity during spike"""
        if not self.user_id:
            self.register_user()
        
        # Multiple quick actions
        for _ in range(random.randint(2, 4)):
            action = random.choice([
                'browse', 'search', 'cart', 'order'
            ])
            
            if action == 'browse':
                self.browse_products()
            elif action == 'search':
                self.search_products()
            elif action == 'cart':
                self.create_cart()
            elif action == 'order' and self.cart_id:
                self.place_order()
    
    @task(1)
    def spike_system_stress(self):
        """Operations that stress system during spike"""
        if not self.user_id:
            self.register_user()
        
        # Rapid sequence of database operations
        for _ in range(random.randint(3, 6)):
            if random.random() < 0.3:
                self.create_cart()
            if random.random() < 0.4:
                self.get_user_orders()
            if random.random() < 0.5:
                self.browse_products()

class FlashSaleUser(RealisticEcommerceUser):
    """Simulates flash sale traffic patterns"""
    
    wait_time = between(0.1, 0.8)  # Very fast during flash sale
    weight = 1
    
    @task(5)
    def flash_sale_frenzy(self):
        """Frenzied activity during flash sale"""
        # Rapid product checks
        self.browse_products()
        
        # Quick purchase decisions
        if not self.user_id:
            self.register_user()
        
        if random.random() < 0.7:  # High purchase intent
            if self.create_cart():
                self.place_order()
    
    @task(2)
    def refresh_product_page(self):
        """Constant page refreshing during flash sale"""
        for _ in range(random.randint(3, 8)):
            self.get_products()
            time.sleep(random.uniform(0.1, 0.3))  # Rapid refreshing
    
    @task(1)
    def competitive_purchasing(self):
        """Competitive purchasing behavior"""
        if not self.user_id:
            self.register_user()
        
        # Multiple rapid purchase attempts
        for _ in range(random.randint(2, 5)):
            if self.create_cart():
                if random.random() < 0.8:  # High completion rate
                    self.place_order()

class SocialMediaTrafficUser(RealisticEcommerceUser):
    """Users arriving from social media links"""
    
    wait_time = between(0.5, 2)
    weight = 1
    
    @task(3)
    def social_media_landing(self):
        """Behavior of users arriving from social media"""
        # Search for trending items
        trending_terms = ["viral", "trending", "popular", "featured"]
        search_term = random.choice(trending_terms)
        
        with self.client.get(f"/product-service/api/products?search={search_term}",
                           name="Social Media Search") as response:
            pass
        
        # Quick browse and potential purchase
        self.browse_products()
        
        if random.random() < 0.3:  # Moderate conversion
            if not self.user_id:
                self.register_user()
            if self.create_cart():
                self.place_order()
    
    @task(2)
    def influencer_driven_purchase(self):
        """Purchases driven by influencer recommendations"""
        if not self.user_id:
            self.register_user()
        
        # Direct product access (following influencer link)
        self.browse_products()
        
        # High intent to purchase
        if random.random() < 0.5:
            if self.create_cart():
                self.place_order()

class EcommerceSpikeTestUser(HttpUser):
    """
    Main spike test user class
    Simulates various spike scenarios
    """
    
    # Distribution for spike testing
    tasks = {
        SpikeTestUser: 50,           # 50% general spike users
        FlashSaleUser: 30,           # 30% flash sale users
        SocialMediaTrafficUser: 20   # 20% social media traffic
    }
    
    wait_time = between(0.3, 1.5)
    
    def on_start(self):
        """Initialize spike test user"""
        print(f"📊 Spike Test User {self.get_user_id()} started")
    
    def on_stop(self):
        """Clean up spike test user"""
        print(f"📊 Spike Test User {self.get_user_id()} stopped")
    
    def get_user_id(self):
        """Get unique user identifier for logging"""
        return getattr(self, '_user_id', id(self))

# Custom event handlers for spike testing
from locust import events

@events.test_start.add_listener
def on_spike_test_start(environment, **kwargs):
    """Called when spike test starts"""
    print("📊 SPIKE TEST STARTED")
    print("Scenario: Sudden traffic spike (e.g., viral social media post)")
    print("Pattern: Rapid user increase → Peak load → Quick decrease")
    print("Watch for: System stability during rapid load changes")

@events.spawning_complete.add_listener
def on_spike_peak(environment, **kwargs):
    """Called when target user count is reached"""
    print(f"📊 SPIKE PEAK REACHED: {environment.runner.user_count} users")
    print("Monitoring system behavior at peak load...")

@events.test_stop.add_listener
def on_spike_test_stop(environment, **kwargs):
    """Called when spike test stops"""
    print("📊 SPIKE TEST COMPLETED")
    
    # Analyze spike test results
    stats = environment.stats
    total_rps = stats.total.current_rps
    avg_response_time = stats.total.avg_response_time
    max_response_time = stats.total.max_response_time
    error_rate = stats.total.fail_ratio
    
    print(f"Peak RPS: {total_rps:.2f}")
    print(f"Average Response Time: {avg_response_time:.2f}ms")
    print(f"Max Response Time: {max_response_time:.2f}ms") 
    print(f"Error Rate: {error_rate:.2%}")
    
    # Spike test evaluation
    if error_rate < 0.05 and avg_response_time < 2000:
        print("✅ System handled spike excellently")
    elif error_rate < 0.15 and avg_response_time < 4000:
        print("⚠️ System handled spike with some degradation")
    else:
        print("❌ System struggled with spike traffic")
    
    print("\n📊 SPIKE TEST RECOMMENDATIONS:")
    if avg_response_time > 2000:
        print("- Consider implementing caching strategies")
        print("- Review database query optimization")
    if error_rate > 0.10:
        print("- Implement rate limiting and throttling") 
        print("- Add circuit breakers for service protection")
    if total_rps < 50:
        print("- Review application server configuration")
        print("- Consider horizontal scaling options")

if __name__ == "__main__":
    """
    Run this spike test with:
    
    # Quick spike test (5 minutes)
    locust -f ecommerce_spike_test.py --host=http://localhost:8100 --users 300 --spawn-rate 50 --run-time 5m --headless
    
    # Extended spike test with multiple waves
    locust -f ecommerce_spike_test.py --host=http://localhost:8100 --users 400 --spawn-rate 80 --run-time 8m --headless
    
    Expected Patterns:
    - Rapid load increase should not crash system
    - Response times may spike initially then stabilize
    - Error rate should remain manageable
    - System should recover quickly after spike
    
    Key Metrics:
    - Peak RPS during spike
    - Response time spike and recovery
    - Error rate during peak load
    - System stability after load decrease
    
    Spike Scenarios:
    - Flash sale announcement
    - Viral social media post
    - News coverage or celebrity endorsement
    - Holiday shopping rush
    """
    pass