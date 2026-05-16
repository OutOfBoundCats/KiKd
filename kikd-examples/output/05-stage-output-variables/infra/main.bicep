targetScope = 'subscription'

param location string
param environment string
param namePrefix string

resource rg__namePrefix___environment_ 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: 'rg-' + namePrefix + '-' + environment
  location: location
}

resource st_namePrefix__environment_ 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: 'st' + namePrefix + environment
  scope: resourceGroup(rg__namePrefix___environment_.name)
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
  }
}
