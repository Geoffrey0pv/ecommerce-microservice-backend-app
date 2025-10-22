"""
Data Generator for E-commerce Performance Testing
Generates realistic test data for users, products, orders, etc.
"""

import random
import string
import uuid
from datetime import datetime, timedelta
from faker import Faker

fake = Faker()

class DataGenerator:
    """Generates realistic test data for e-commerce scenarios"""
    
    def __init__(self):
        self.fake = Faker()
        self.used_emails = set()
        self.used_usernames = set()
        
    def generate_unique_email(self):
        """Generate a unique email address"""
        max_attempts = 10
        for _ in range(max_attempts):
            email = self.fake.email()
            if email not in self.used_emails:
                self.used_emails.add(email)
                return email
        
        # Fallback: add timestamp to ensure uniqueness
        base_email = self.fake.email()
        timestamp = str(int(datetime.now().timestamp()))
        email = base_email.replace('@', f'+{timestamp}@')
        self.used_emails.add(email)
        return email
    
    def generate_unique_username(self):
        """Generate a unique username"""
        max_attempts = 10
        for _ in range(max_attempts):
            username = self.fake.user_name()
            if username not in self.used_usernames:
                self.used_usernames.add(username)
                return username
        
        # Fallback: add random suffix
        base_username = self.fake.user_name()
        suffix = ''.join(random.choices(string.digits, k=4))
        username = f"{base_username}{suffix}"
        self.used_usernames.add(username)
        return username
    
    def generate_user_data(self):
        """Generate realistic user registration data"""
        return {
            "firstName": self.fake.first_name(),
            "lastName": self.fake.last_name(),
            "email": self.generate_unique_email(),
            "username": self.generate_unique_username(),
            "password": "TestPass123!",
            "phone": self.fake.phone_number()[:15],  # Limit phone length
            "imageUrl": f"https://picsum.photos/200/200?random={random.randint(1, 1000)}",
            "credentialType": "EMAIL"
        }
    
    def generate_product_data(self):
        """Generate realistic product data"""
        categories = ["Electronics", "Clothing", "Books", "Home", "Sports", "Beauty"]
        
        return {
            "productTitle": self.fake.catch_phrase(),
            "imageUrl": f"https://picsum.photos/300/300?random={random.randint(1, 1000)}",
            "sku": f"SKU{random.randint(10000, 99999)}",
            "priceUnit": round(random.uniform(10.0, 500.0), 2),
            "quantity": random.randint(10, 1000),
            "categoryTitle": random.choice(categories),
            "productDesc": self.fake.text(max_nb_chars=200)
        }
    
    def generate_cart_data(self, user_id):
        """Generate cart data for a user"""
        return {
            "userId": user_id
        }
    
    def generate_order_data(self, cart_id):
        """Generate realistic order data"""
        return {
            "orderDate": datetime.now().isoformat(),
            "orderDesc": f"Order for cart {cart_id}",
            "orderFee": round(random.uniform(15.0, 200.0), 2),
            "cartId": cart_id
        }
    
    def generate_payment_data(self, order_id, amount):
        """Generate realistic payment data"""
        payment_methods = ["CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER"]
        
        return {
            "orderId": order_id,
            "paymentDate": datetime.now().isoformat(),
            "paymentMethod": random.choice(payment_methods),
            "amount": amount,
            "paymentStatus": "PENDING"
        }
    
    def generate_shipping_data(self, order_id):
        """Generate realistic shipping data"""
        shipping_companies = ["FedEx", "UPS", "DHL", "USPS", "Local Delivery"]
        
        return {
            "orderId": order_id,
            "shippingDate": (datetime.now() + timedelta(days=random.randint(1, 3))).isoformat(),
            "shippingAddress": self.fake.address().replace('\n', ', ')[:100],
            "shippingCompany": random.choice(shipping_companies),
            "estimatedDelivery": (datetime.now() + timedelta(days=random.randint(5, 14))).isoformat()
        }
    
    def generate_random_search_term(self):
        """Generate realistic search terms"""
        search_terms = [
            "laptop", "smartphone", "headphones", "book", "shirt", "shoes",
            "watch", "camera", "tablet", "keyboard", "mouse", "monitor",
            "jacket", "jeans", "sneakers", "backpack", "wallet", "sunglasses"
        ]
        return random.choice(search_terms)
    
    def generate_user_session_data(self):
        """Generate data for a complete user session"""
        user_data = self.generate_user_data()
        session_id = str(uuid.uuid4())
        
        return {
            "session_id": session_id,
            "user_data": user_data,
            "products_to_view": random.randint(3, 10),
            "search_terms": [self.generate_random_search_term() for _ in range(random.randint(1, 3))],
            "items_to_add_to_cart": random.randint(1, 5),
            "likelihood_to_purchase": random.uniform(0.3, 0.9)
        }

# Singleton instance for global use
data_generator = DataGenerator()