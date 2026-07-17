with open('app/src/main/res/values/strings.xml', 'r') as f:
    content = f.read()

content = content.replace('    <string name="default_web_client_id">YOUR_CLIENT_ID</string>\n', '')

with open('app/src/main/res/values/strings.xml', 'w') as f:
    f.write(content)
