# Secure an app with Auth0

<div style="display: flex; align-items: center; gap: .5rem;">
<span style="font-weight: bold">Route plugins:</span>
<a class="badge" href="https://maif.github.io/otoroshi/manual/plugins/built-in-plugins.html#otoroshi.next.plugins.AuthModule">Authentication</a>
</div>

### Download Otoroshi

@@include[initialize.md](../includes/initialize.md) { #initialize-otoroshi }

### Configure an Auth0 client

The first step of this tutorial is to setup an Auth0 application with the information of the instance of our Otoroshi.

Navigate to @link:[https://manage.auth0.com](https://manage.auth0.com) { open=new } (create an account if it's not already done). 

Let's create an application when clicking on the **Applications** button on the sidebar. Then click on the **Create application** button on the top right.

1. Choose `Regular Web Applications` as `Application type`
2. Then set for example `otoroshi-client` as `Name`, and confirm the creation
3. Jump to the `Settings` tab
4. Scroll to the `Application URLs` section and add the following url as `Allowed Callback URLs` : `http://otoroshi.oto.tools:8080/backoffice/auth0/callback`
5. Set `https://otoroshi.oto.tools:8080/` as `Allowed Logout URLs`
6. Set `https://otoroshi.oto.tools:8080` as `Allowed Web Origins` 
7. Save changes at the bottom of the page.

Once done, we have a full setup, with a client ID and secret at the top of the page, which authorizes our Otoroshi and redirects the user to the callback url when they log into Auth0.

### Create an Auth0 provider module

Let's back to Otoroshi to create an authentication module with `OAuth2 / OIDC provider` as `type`.

1. Go ahead, and navigate to @link:[http://otoroshi.oto.tools:8080](http://otoroshi.oto.tools:8080) { open=new }
1. Click on the cog icon on the top right
1. Then `Authentication configs` button
1. And add a new configuration when clicking on the `Add item` button
2. Select the `OAuth provider` in the type selector field
3. Then click on `Get from OIDC config` and paste `https://<tenant-name>.<region>.auth0.com/.well-known/openid-configuration`. Replace the tenant name by the name of your tenant (displayed on the left top of auth0 page), and the region of the tenant (`eu` in my case).

Once done, set the `Client ID` and the `Client secret` from your Auth0 application. End the configuration with `http://otoroshi.oto.tools:8080/backoffice/auth0/callback` as `Callback URL`.

At the bottom of the page, disable the `secure` button (because we're using http and this configuration avoid to include cookie in an HTTP Request without secure channel, typically HTTPs).

### Connect to Otoroshi with Auth0 authentication

To secure Otoroshi with your Auth0 configuration, we have to register an **Authentication configuration** as a BackOffice Auth. configuration.

1. Navigate to the **danger zone** (when clicking on the cog on the top right and selecting Danger zone)
2. Scroll to the **BackOffice auth. settings**
3. Select your last Authentication configuration (created in the previous section)
4. Save the global configuration with the button on the top right

#### Testing your configuration

1. Disconnect from your instance
1. Then click on the *Login using third-party* button (or navigate to http://otoroshi.oto.tools:8080)
2. Click on **Login using Third-party** button
3. If all is configured, Otoroshi will redirect you to the auth0 server login page
4. Set your account credentials
5. Good works! You're connected to Otoroshi with an Auth0 module.

### Secure an app with Auth0 authentication

With the previous configuration, you can secure any of Otoroshi services with it. 

The first step is to apply a little change on the previous configuration. 

1. Navigate to @link:[http://otoroshi.oto.tools:8080/bo/dashboard/auth-configs](http://otoroshi.oto.tools:8080/bo/dashboard/auth-configs) { open=new }.
2. Create a new **Authentication module** configuration with the same values.
3. Replace the `Callback URL` field to `http://privateapps.oto.tools:8080/privateapps/generic/callback` (we changed this value because the redirection of a connected user by a third-party server is covered by another route by Otoroshi).
4. Disable the `secure` button (because we're using http and this configuration avoid to include cookie in an HTTP Request without secure channel, typically HTTPs)

> Note : an Otoroshi service is called **a private app** when it is protected by an Authentication module.

We can set the Authentication module on your route.

1. Navigate to any created route
2. Search in the list of plugins the plugin named `Authentication`
3. Select your Authentication config inside the list
4. Don't forget to save your configuration.
5. Now you can try to call your route and see the Auth0 login page appears.


