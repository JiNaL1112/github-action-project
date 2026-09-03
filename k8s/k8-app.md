# 1. Namespace — everything else needs this to exist first
kubectl apply -f k8s/rbac/namespace.yaml

# 2. ConfigMap — no dependencies, safe to apply early
kubectl apply -f k8s/mysql/mysql-configmap.yaml

# 3. PVC — mysql pod needs this bound before it can start
kubectl apply -f k8s/mysql/mysql-pvc.yaml

# 4. SecretProviderClass — this is what syncs bankapp-secret and mysql-secret 
#    from Azure Key Vault into actual k8s Secret objects
kubectl apply -f k8s/keyvault/secret-provider-class.yaml

# 5. bankapp Deployment/Service — mounts the CSI volume, which is what 
#    actually triggers the sync of DB_USERNAME/DB_PASSWORD/MYSQL_ROOT_PASSWORD
kubectl apply -f k8s/bankapp/bankapp-deployment.yaml
kubectl apply -f k8s/bankapp/bankapp-service.yaml

# 6. mysql Deployment/Service
kubectl apply -f k8s/mysql/mysql-deployment.yaml
kubectl apply -f k8s/mysql/mysql-service.yaml