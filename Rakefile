require 'yaml'
require 'rake_ssh'
require 'rake_github'
require 'rake_circle_ci'
require 'rake_leiningen'

task :default => [:'library:check', :'library:test:unit']

RakeLeiningen.define_installation_tasks(
    version: '2.9.1')

RakeSSH.define_key_tasks(
    namespace: :deploy_key,
    path: 'config/secrets/ci',
    comment: 'maintainers@logicblocks.io'
)

RakeCircleCI.define_project_tasks(
    namespace: :circle_ci,
    project_slug: 'github/logicblocks/salutem'
) do |t|
  circle_ci_config =
      YAML.load_file('config/secrets/circle_ci/config.yaml')

  t.api_token = circle_ci_config["circle_ci_api_token"]
  t.environment_variables = {
      ENCRYPTION_PASSPHRASE:
          File.read('config/secrets/ci/encryption.passphrase')
              .chomp
  }
  t.ssh_keys = [
      {
          hostname: "github.com",
          private_key: File.read('config/secrets/ci/ssh.private')
      }
  ]
end

RakeGithub.define_repository_tasks(
    namespace: :github,
    repository: 'logicblocks/salutem',
) do |t|
  github_config =
      YAML.load_file('config/secrets/github/config.yaml')

  t.access_token = github_config["github_personal_access_token"]
  t.deploy_keys = [
      {
          title: 'CircleCI',
          public_key: File.read('config/secrets/ci/ssh.public')
      }
  ]
end

namespace :pipeline do
  task :prepare => [
      :'circle_ci:project:follow',
      :'circle_ci:env_vars:ensure',
      :'circle_ci:ssh_keys:ensure',
      :'github:deploy_keys:ensure'
  ]
end

namespace :library do
  RakeLeiningen.define_check_tasks(fix: true)

  namespace :test do
    RakeLeiningen.define_test_task(
        name: :unit, type: 'unit', profile: 'test')
  end

  namespace :publish do
    RakeLeiningen.define_release_task(
        name: :prerelease,
        profile: 'prerelease')

    RakeLeiningen.define_release_task(
        name: :release,
        profile: 'release')
  end
end
