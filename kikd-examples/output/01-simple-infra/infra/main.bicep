targetScope = 'subscription'

param location string

resource rg_kikd_simple 'Microsoft.Resources/resourceGroups@2022-09-01' = {
  name: 'rg-kikd-simple'
  location: location
}
