targetScope = 'subscription'

param location string
param environment string = 'dev'
param namePrefix string = 'kikd'

resource rg__namePrefix___environment_ 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: 'rg-' + namePrefix + '-' + environment
  location: location
}

resource vnet__namePrefix___environment_ 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: 'vnet-' + namePrefix + '-' + environment
  scope: resourceGroup(rg__namePrefix___environment_.name)
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [
        '10.20.0.0/16'
      ]
    }
  }
}

resource st_namePrefix__environment_ 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: 'st' + namePrefix + environment
  scope: resourceGroup(rg__namePrefix___environment_.name)
  location: location
  tags: {
    system: 'kikd'
  }
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
  }
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
      appSettings: [
        {
          name: 'WEBSITE_RUN_FROM_PACKAGE'
          value: '1'
        }
      ]
    }
  }
}
