using './main.bicep'
param location = 'eastus'
param environment = readEnvironmentVariable('ENVIRONMENT', 'dev')
param namePrefix = readEnvironmentVariable('NAMEPREFIX', 'kikd')
