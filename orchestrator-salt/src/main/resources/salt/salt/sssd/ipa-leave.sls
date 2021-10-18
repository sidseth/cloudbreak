{%- from 'sssd/settings.sls' import ipa with context %}
{%- from 'metadata/settings.sls' import metadata with context %}

{% if metadata.platform == 'YARN' and not metadata.cluster_in_childenvironment %}
{%- if "manager_server" in grains.get('roles', []) %}

create_remove_cm_sa_script:
  file.managed:
    - name: /opt/salt/scripts/remove_cm_sa.sh
    - source: salt://sssd/template/remove_cm_sa.j2
    - makedirs: True
    - template: jinja
    - context:
        ipa: {{ ipa }}
    - mode: 755

remove_cm_service_account:
  cmd.run:
    - name: sh /opt/salt/scripts/remove_cm_sa.sh 2>&1 | tee -a /var/log/remove_cm_sa.log && exit ${PIPESTATUS[0]}
    - env:
        - password: "{{salt['pillar.get']('sssd-ipa:password')}}"
    - onlyif: ls /etc/cloudera-scm-server/cmf.keytab
    - require:
      - file: create_remove_cm_sa_script

{%- endif %}
{% endif %}

/opt/salt/scripts/remove_dns2.sh:
  file.managed:
    - makedirs: True
    - user: root
    - group: root
    - mode: 700
    - source: salt://sssd/template/remove_dns2.j2
    - template: jinja

leave-ipa:
  cmd.run:
{% if metadata.platform != 'YARN' %}
    - name: echo $PW | kinit {{ salt['pillar.get']('sssd-ipa:principal') }} &&  sh /opt/salt/scripts/remove_dns2.sh | tee -a /var/log/remove_dns2.log
{% else %}
    - name: runuser -l root -c 'echo $PW | kinit {{ salt['pillar.get']('sssd-ipa:principal') }} && ipa host-del {{ salt['grains.get']('fqdn') }} --updatedns && ipa-client-install --uninstall -U'
{% endif %}
    - onlyif: ipa env
    - env:
        - PW: "{{salt['pillar.get']('sssd-ipa:password')}}"

leave-ipa2:
  cmd.run:
    - name: echo $PW | kinit {{ salt['pillar.get']('sssd-ipa:principal') }} && ipa-client-install --uninstall -U
    - onlyif: ipa env
    - env:
        - PW: "{{salt['pillar.get']('sssd-ipa:password')}}"