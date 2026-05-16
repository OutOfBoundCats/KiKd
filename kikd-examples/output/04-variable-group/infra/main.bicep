targetScope = 'subscription'

param location string
param environment string
param namePrefix string

resource rg__namePrefix___environment_ 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: 'rg-' + namePrefix + '-' + environment
  location: location
}

resource plan__namePrefix___environment_ 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: 'plan-' + namePrefix + '-' + environment
  scope: resourceGroup(rg__namePrefix___environment_.name)
  location: location
  sku: {
    name: 'B1'
  }
  kind: 'linux'
  properties: {
    reserved: true
  }
}

resource app__namePrefix___environment_ 'Microsoft.Web/sites@2023-12-01' = {
  name: 'app-' + namePrefix + '-' + environment
  scope: resourceGroup(rg__namePrefix___environment_.name)
  location: location
  properties: {
    serverFarmId: plan__namePrefix___environment_.id
    siteConfig: {
      appSettings: []
    }
  }
}
