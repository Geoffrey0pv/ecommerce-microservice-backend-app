"""
User Behavior Patterns for E-commerce Performance Testing
Realistic user behaviors and workflows for Locust testing
"""

import random
import time
from locust import HttpUser, task, between
from data_generator import data_generator

class BaseEcommerceUser(HttpUser):
    """Base class for e-commerce user behaviors"""
    
    wait_time = between(1, 3)  # Wait 1-3 seconds between requests
    
    def on_start(self):
        """Called when a user starts - setup user session"""
        self.session_data = data_generator.generate_user_session_data()
        self.user_id = None
        self.cart_id = None
        self.products = []
        self.created_orders = []
        
        # Try to get initial products list
        self.get_products()
    
    def get_products(self):
        """Get list of available products"""
        with self.client.get("/product-service/api/products", 
                           catch_response=True, 
                           name="Get Products") as response:
            if response.status_code == 200:
                try:
                    self.products = response.json()
                    response.success()
                except:
                    self.products = []
                    response.failure("Failed to parse products JSON")
            else:
                response.failure(f"Got status code {response.status_code}")
    
    def register_user(self):
        """Register a new user"""
        user_data = self.session_data["user_data"]
        
        with self.client.post("/user-service/api/users", 
                            json=user_data,
                            catch_response=True,
                            name="Register User") as response:
            if response.status_code in [200, 201]:
                try:
                    result = response.json()
                    self.user_id = result.get("userId")
                    response.success()
                    return True
                except:
                    response.failure("Failed to parse user registration response")
            else:
                response.failure(f"Registration failed with status {response.status_code}")
            return False
    
    def browse_products(self):
        """Browse products (realistic user behavior)"""
        # Get all products
        self.get_products()
        
        # Browse random products
        if self.products:
            products_to_view = min(len(self.products), 
                                 self.session_data["products_to_view"])
            
            for _ in range(products_to_view):
                product = random.choice(self.products)
                product_id = product.get("productId")
                
                if product_id:
                    with self.client.get(f"/product-service/api/products/{product_id}",
                                       catch_response=True,
                                       name="View Product Detail") as response:
                        if response.status_code == 200:
                            response.success()
                        else:
                            response.failure(f"Product detail failed: {response.status_code}")
                
                # Simulate user thinking time
                time.sleep(random.uniform(0.5, 2.0))
    
    def search_products(self):
        """Search for products"""
        for search_term in self.session_data["search_terms"]:
            with self.client.get(f"/product-service/api/products?search={search_term}",
                               catch_response=True,
                               name="Search Products") as response:
                if response.status_code == 200:
                    response.success()
                else:
                    response.failure(f"Search failed: {response.status_code}")
            
            time.sleep(random.uniform(0.3, 1.0))
    
    def create_cart(self):
        """Create a shopping cart"""
        if not self.user_id:
            return False
            
        cart_data = data_generator.generate_cart_data(self.user_id)
        
        with self.client.post("/order-service/api/carts",
                            json=cart_data,
                            catch_response=True,
                            name="Create Cart") as response:
            if response.status_code in [200, 201]:
                try:
                    result = response.json()
                    self.cart_id = result.get("cartId")
                    response.success()
                    return True
                except:
                    response.failure("Failed to parse cart creation response")
            else:
                response.failure(f"Cart creation failed: {response.status_code}")
            return False
    
    def place_order(self):
        """Place an order"""
        if not self.cart_id:
            return False
            
        order_data = data_generator.generate_order_data(self.cart_id)
        
        with self.client.post("/order-service/api/orders",
                            json=order_data,
                            catch_response=True,
                            name="Place Order") as response:
            if response.status_code in [200, 201]:
                try:
                    result = response.json()
                    order_id = result.get("orderId")
                    if order_id:
                        self.created_orders.append(order_id)
                    response.success()
                    return True
                except:
                    response.failure("Failed to parse order response")
            else:
                response.failure(f"Order placement failed: {response.status_code}")
            return False
    
    def get_user_orders(self):
        """Get user's order history"""
        if not self.user_id:
            return
            
        with self.client.get(f"/order-service/api/orders?userId={self.user_id}",
                           catch_response=True,
                           name="Get User Orders") as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Get orders failed: {response.status_code}")

class CasualBrowser(BaseEcommerceUser):
    """User who mostly browses without purchasing"""
    
    @task(5)
    def browse_products_task(self):
        self.browse_products()
    
    @task(3)
    def search_products_task(self):
        self.search_products()
    
    @task(1)
    def register_and_browse(self):
        if not self.user_id:
            self.register_user()
        self.browse_products()

class ActiveShopper(BaseEcommerceUser):
    """User who browses and occasionally makes purchases"""
    
    @task(3)
    def browse_products_task(self):
        self.browse_products()
    
    @task(2)
    def search_and_browse(self):
        self.search_products()
        self.browse_products()
    
    @task(2)
    def complete_shopping_journey(self):
        # Register if needed
        if not self.user_id:
            if not self.register_user():
                return
        
        # Browse products
        self.browse_products()
        
        # Create cart and place order (30% chance)
        if random.random() < 0.3:
            if self.create_cart():
                time.sleep(random.uniform(1, 3))  # Decision time
                self.place_order()
    
    @task(1)
    def check_order_history(self):
        if self.user_id:
            self.get_user_orders()

class FrequentBuyer(BaseEcommerceUser):
    """User who frequently makes purchases"""
    
    @task(2)
    def browse_products_task(self):
        self.browse_products()
    
    @task(3)
    def complete_purchase_flow(self):
        # Ensure user is registered
        if not self.user_id:
            if not self.register_user():
                return
        
        # Search for specific items
        self.search_products()
        
        # Browse and decide to purchase (60% chance)
        self.browse_products()
        
        if random.random() < 0.6:
            if self.create_cart():
                time.sleep(random.uniform(0.5, 2.0))  # Quick decision
                self.place_order()
    
    @task(1)
    def check_order_history(self):
        if self.user_id:
            self.get_user_orders()

class PowerUser(BaseEcommerceUser):
    """Advanced user who uses the system extensively"""
    
    wait_time = between(0.5, 1.5)  # Faster interactions
    
    @task(2)
    def rapid_browsing(self):
        self.browse_products()
        self.search_products()
    
    @task(3)
    def multiple_purchases(self):
        if not self.user_id:
            if not self.register_user():
                return
        
        # Make multiple quick purchases
        for _ in range(random.randint(1, 3)):
            if self.create_cart():
                self.place_order()
                time.sleep(random.uniform(0.2, 1.0))
    
    @task(1)
    def check_everything(self):
        if self.user_id:
            self.get_user_orders()
            self.browse_products()

# User behavior distribution for realistic load
class RealisticEcommerceUser(BaseEcommerceUser):
    """Realistic mix of user behaviors"""
    
    def on_start(self):
        super().on_start()
        
        # Assign user type based on realistic distribution
        user_type_rand = random.random()
        
        if user_type_rand < 0.5:  # 50% casual browsers
            self.user_type = "casual"
        elif user_type_rand < 0.8:  # 30% active shoppers
            self.user_type = "active"
        elif user_type_rand < 0.95:  # 15% frequent buyers
            self.user_type = "frequent"
        else:  # 5% power users
            self.user_type = "power"
            self.wait_time = between(0.5, 1.5)
    
    @task
    def behave_according_to_type(self):
        if self.user_type == "casual":
            # Mostly browse
            if random.random() < 0.7:
                self.browse_products()
            else:
                self.search_products()
        
        elif self.user_type == "active":
            # Browse and occasionally purchase
            self.browse_products()
            if random.random() < 0.3:
                if not self.user_id:
                    self.register_user()
                if self.create_cart():
                    self.place_order()
        
        elif self.user_type == "frequent":
            # Regular purchases
            if not self.user_id:
                self.register_user()
            
            self.search_products()
            self.browse_products()
            
            if random.random() < 0.6:
                if self.create_cart():
                    self.place_order()
        
        elif self.user_type == "power":
            # Heavy usage
            if not self.user_id:
                self.register_user()
            
            # Rapid interactions
            self.browse_products()
            if random.random() < 0.4:
                if self.create_cart():
                    self.place_order()
            
            if random.random() < 0.3:
                self.get_user_orders()