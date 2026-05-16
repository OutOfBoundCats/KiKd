targetScope = 'subscription'

param location string

resource rg_kikd_network 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: 'rg-kikd-network'
  location: location
}

resource vnet_kikd_network 'Microsoft.Network/virtualNetworks@2025-01-01' = {
  name: 'vnet-kikd-network'
  scope: resourceGroup(rg_kikd_network.name)
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [
        '10.0.0.0/16'
      ]
    }
    subnets: [
      {
        name: 'Subnet-1'
        properties: {
          addressPrefix: '10.0.0.0/24'
        }
      }
      {
        name: 'Subnet-2'
        properties: {
          addressPrefix: '10.0.1.0/24'
        }
      }
    ]
  }
}
