# frozen_string_literal: true

require 'confidante'
require 'rake_circle_ci'
require 'rake_docker'
require 'rake_git'
require 'rake_git_crypt'
require 'rake_github'
require 'rake_gpg'
require 'rake_leiningen'
require 'rake_ssh'
require 'rubocop/rake_task'
require 'yaml'

task default: %i[
  build:code:fix
  library:initialise
  library:check
  library:test:all
]

RubyLeiningen::Commands.define_custom_command('install')

RakeLeiningen.define_installation_tasks(
  version: '2.10.0'
)

RakeGitCrypt.define_standard_tasks(
  namespace: :git_crypt,

  provision_secrets_task_name: :'secrets:provision',
  destroy_secrets_task_name: :'secrets:destroy',

  install_commit_task_name: :'git:commit',
  uninstall_commit_task_name: :'git:commit',

  gpg_user_key_paths: %w[
    config/gpg
    config/secrets/ci/gpg.public
  ]
)

namespace :git do
  RakeGit.define_commit_task(
    argument_names: [:message]
  ) do |t, args|
    t.message = args.message
  end
end

namespace :encryption do
  namespace :directory do
    desc 'Ensure CI secrets directory exists.'
    task :ensure do
      FileUtils.mkdir_p('config/secrets/ci')
    end
  end

  namespace :passphrase do
    desc 'Generate encryption passphrase for CI GPG key'
    task generate: ['directory:ensure'] do
      File.write(
        'config/secrets/ci/encryption.passphrase',
        SecureRandom.base64(36)
      )
    end
  end
end

namespace :keys do
  namespace :deploy do
    RakeSSH.define_key_tasks(
      path: 'config/secrets/ci/',
      comment: 'maintainers@logicblocks.io'
    )
  end

  namespace :secrets do
    namespace :gpg do
      RakeGPG.define_generate_key_task(
        output_directory: 'config/secrets/ci',
        name_prefix: 'gpg',
        owner_name: 'LogicBlocks Maintainers',
        owner_email: 'maintainers@logicblocks.io',
        owner_comment: 'salutem CI Key'
      )
    end

    task generate: ['gpg:generate']
  end
end

namespace :secrets do
  namespace :directory do
    desc 'Ensure secrets directory exists and is set up correctly'
    task :ensure do
      FileUtils.mkdir_p('config/secrets')
      unless File.exist?('config/secrets/.unlocked')
        File.write('config/secrets/.unlocked',
                   'true')
      end
    end
  end

  desc 'Generate all generatable secrets.'
  task regenerate: %w[
    encryption:passphrase:generate
    keys:deploy:generate
    keys:secrets:generate
  ]

  desc 'Provision all secrets.'
  task provision: [:regenerate]

  desc 'Delete all secrets.'
  task :destroy do
    rm_rf 'config/secrets'
  end

  desc 'Rotate all secrets.'
  task rotate: [:'git_crypt:reinstall']
end

RakeCircleCI.define_project_tasks(
  namespace: :circle_ci,
  project_slug: 'github/logicblocks/salutem'
) do |t|
  circle_ci_config =
    YAML.load_file('config/secrets/circle_ci/config.yaml')

  t.api_token = circle_ci_config['circle_ci_api_token']
  t.environment_variables = {
    ENCRYPTION_PASSPHRASE:
        File.read('config/secrets/ci/encryption.passphrase')
          .chomp
  }
  t.checkout_keys = []
  t.ssh_keys = [
    {
      hostname: 'github.com',
      private_key: File.read('config/secrets/ci/ssh.private')
    }
  ]
end

RakeGithub.define_repository_tasks(
  namespace: :github,
  repository: 'logicblocks/salutem'
) do |t|
  github_config =
    YAML.load_file('config/secrets/github/config.yaml')

  t.access_token = github_config['github_personal_access_token']
  t.deploy_keys = [
    {
      title: 'CircleCI',
      public_key: File.read('config/secrets/ci/ssh.public')
    }
  ]
end

namespace :pipeline do
  desc 'Prepare CircleCI Pipeline'
  task prepare: %i[
    circle_ci:env_vars:ensure
    circle_ci:checkout_keys:ensure
    circle_ci:ssh_keys:ensure
    github:deploy_keys:ensure
  ]
end

RuboCop::RakeTask.new

namespace :build do
  namespace :code do
    desc 'Run all checks on the test code'
    task check: [:rubocop]

    desc 'Attempt to automatically fix issues with the test code'
    task fix: [:'rubocop:autocorrect_all']
  end
end

namespace :library do
  desc 'Initialise all modules in the local maven repository'
  task initialise: [:'leiningen:ensure'] do
    RubyLeiningen.install
  end

  RakeLeiningen.define_check_tasks(fix: true)

  namespace :test do
    RakeLeiningen.define_test_task(
      name: :unit,
      type: 'unit',
      profile: 'unit'
    )

    RakeLeiningen.define_test_task(
      name: :integration,
      type: 'integration',
      profile: 'integration'
    ) do
      Rake::Task['database:test:provision'].invoke unless ENV['CI'] == 'true'
    end

    RakeLeiningen.define_test_task(
      name: :performance,
      type: 'performance',
      profile: 'performance'
    )

    task all: %i[unit integration performance]
  end

  namespace :publish do
    RakeLeiningen.define_release_task(
      name: :prerelease,
      profile: 'prerelease'
    )

    RakeLeiningen.define_release_task(
      name: :release,
      profile: 'release'
    )
  end
end

namespace :database do
  namespace :test do
    RakeDocker.define_container_tasks(
      container_name: 'salutem-test-database'
    ) do |t|
      t.image = 'postgres:11.12'
      t.ports = ['5432:5432']
      t.environment = %w[
        POSTGRES_DB=test
        POSTGRES_PASSWORD=test-password
        POSTGRES_USER=tester
      ]
    end
  end
end
