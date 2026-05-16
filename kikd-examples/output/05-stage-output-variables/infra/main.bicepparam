using './main.bicep'
param location = 'eastus'
param environment = readEnvironmentVariable('ENVIRONMENT')
param namePrefix = readEnvironmentVariable('NAMEPREFIX')
